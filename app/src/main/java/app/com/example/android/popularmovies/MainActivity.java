package app.com.example.android.popularmovies;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import app.com.example.android.popularmovies.sync.PopularMoviesSyncAdapter;

import static app.com.example.android.popularmovies.sync.PopularMoviesSyncAdapter.syncImmediately;

public class MainActivity extends ActionBarActivity {

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final String DETAILFRAGMENT_TAG = "DFTAG";
    private static final String HINTFRAGMENT_TAG = "HFTAG";

    private static final int MSG_NONE_ITEM_IN_LIST = 1001;
    private static final int MSG_FIRST_LOADING_FINISHED = 1002;
    private static final int MSG_FIRST_LOADING_FAILED = 1001;

    private static final String ISSHOWCOLLECTION_KEY = "isshwocollection";

    private boolean mTwoPane;

    private String mMode;
    private String mSyncInterval;

    private boolean mIsShowCollection = false;

    private static MenuItem mShowCollectionItem;

    private ProgressDialog mProgressDialog;
    private boolean mIsFirstLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mMode = Utility.getPreferredMode(this);
        mSyncInterval = Utility.getPreferredSyncInterval(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (findViewById(R.id.detail_container) != null) {
            // The detail container view will be present only in the large-screen layouts
            // (res/layout-sw600dp). If this view is present, then the activity should be
            // in two-pane mode.
            mTwoPane = true;
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
            if (savedInstanceState == null) {
                showHintInDetailContainerOrToast("请点击电影海报查看电影详细信息");
            }
        } else {
            mTwoPane = false;
            getSupportActionBar().setElevation(0f);
        }

        //读取是否收藏
        if (savedInstanceState != null) {
            Logger.d(LOG_TAG, "savedInstanceState isn't null");
            if (savedInstanceState.containsKey(ISSHOWCOLLECTION_KEY)) {
                mIsShowCollection = savedInstanceState.getBoolean(ISSHOWCOLLECTION_KEY);
                Logger.d("onCreate", "mIsShowCollection is " + mIsShowCollection);
            }
        }

        PopularMoviesSyncAdapter.initializeSyncAdapter(this);
        Logger.d(LOG_TAG, "initializeSyncAdapter");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);


        mShowCollectionItem = menu.findItem(R.id.action_showCollection);

        //判断该电影是否被收藏，并以此显示对应的菜单“收藏”或“取消收藏”。
        if (mIsShowCollection) {
            mShowCollectionItem.setTitle(getString(R.string.action_showCollection_showAllMovies));//“全部电影列表”
        } else {
            mShowCollectionItem.setTitle(getString(R.string.action_showCollection));//“我的收藏列表”
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.action_showCollection) {
            if (!mIsShowCollection) {//
                item.setTitle(getString(R.string.action_showCollection_showAllMovies));//“全部电影列表”
                mIsShowCollection = true;
                MovieFragment mf = (MovieFragment) getSupportFragmentManager().findFragmentById(R.id.main_container);
                if (null != mf) {
                    mf.onIsShowCollectionChanged(mIsShowCollection);
                }
                showHintInDetailContainerOrToast("电影列表已变更为我的收藏列表");
            } else {
                item.setTitle(getString(R.string.action_showCollection));//“我的收藏列表”
                mIsShowCollection = false;
                MovieFragment mf = (MovieFragment) getSupportFragmentManager().findFragmentById(R.id.main_container);
                if (null != mf) {
                    mf.onIsShowCollectionChanged(mIsShowCollection);
                }
                showHintInDetailContainerOrToast("电影列表已变更为全部电影列表");
            }
            return true;
        }
        if (id == R.id.action_refresh) {
            if (!NetworkUtil.getConnectivityStatus(this)) {
                ToastUtil.show(this, "无网络连接，刷新失败");
                return super.onOptionsItemSelected(item);
            }
            if (mProgressDialog == null) {
                mProgressDialog = new ProgressDialog(MainActivity.this);
                mProgressDialog.setMessage("正在加载中...");
                mProgressDialog.setCanceledOnTouchOutside(false);
            }
            mProgressDialog.show();
            syncImmediately(this);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String mode = Utility.getPreferredMode(this);
        String syncInterval = Utility.getPreferredSyncInterval(this);
        Logger.d(LOG_TAG, "onResume");
        // update the mode in our second pane using the fragment manager
        if (mode != null && !mode.equals(mMode)) {
            MovieFragment mf = (MovieFragment) getSupportFragmentManager().findFragmentById(R.id.main_container);
            if (null != mf) {
                mf.onModeChanged(mIsShowCollection);
            }
            showHintInDetailContainerOrToast("电影列表更改排序方式");

            mMode = mode;
        }

        if (syncInterval != null && !syncInterval.equals(mSyncInterval)) {
            mSyncInterval = syncInterval;
            PopularMoviesSyncAdapter.changeSyncInterval(this, mSyncInterval);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    private void showHintInDetailContainerOrToast(String hint) {
        if (mTwoPane) {
            Bundle args = new Bundle();
            args.putString(HintFragment.HINT, hint);

            HintFragment fragment = new HintFragment();
            fragment.setArguments(args);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.detail_container, fragment, HINTFRAGMENT_TAG)
                    .commit();
        } else {
            ToastUtil.show(this, hint);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // When tablets rotate, the currently selected list item needs to be saved.
        // When no item is selected, mPosition will be set to Listview.INVALID_POSITION,
        // so check for that before storing.
        outState.putBoolean(ISSHOWCOLLECTION_KEY, mIsShowCollection);
        super.onSaveInstanceState(outState);
    }

    @Subscribe
    public void onCancelCollection(MessageEvent event) {
        if (event.msg.equals("onCancelCollection") && mTwoPane && mIsShowCollection) {
            showHintInDetailContainerOrToast("该电影不在收藏列表，请重新选择");
        }
    }

    @Subscribe
    public void onItemSelected(MessageEvent event) {
        if (event.msg.equals("onItemSelected") && (event.object instanceof Uri)) {
            Uri contentUri = (Uri) event.object;

            if (mTwoPane) {
                // In two-pane mode, show the detail view in this activity by
                // adding or replacing the detail fragment using a
                // fragment transaction.
                Bundle args = new Bundle();
                args.putParcelable(DetailFragment.DETAIL_URI, contentUri);

                DetailFragment fragment = new DetailFragment();
                fragment.setArguments(args);

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.detail_container, fragment, DETAILFRAGMENT_TAG)
                        .commit();
                Logger.d("Click", "mTwoPane is true ");
            } else {
                Intent intent = new Intent(this, DetailActivity.class)
                        .setData(contentUri);
                startActivity(intent);
                Logger.d("Click", "mTwoPane is false ");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNoneItemInList(MessageEvent event) {
        if (event.msg.equals("onNoneItemInList") && !mIsShowCollection) {
            if (!mIsFirstLoading){
                //当列表中没有数据时，立刻同步
//                syncImmediately(this);
                handler.sendEmptyMessage(MSG_NONE_ITEM_IN_LIST);
                mIsFirstLoading = true;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLoadingFinished(MessageEvent event) {
        if (event.msg.equals("onLoadingFinished") && mIsFirstLoading) {
            handler.sendEmptyMessage(MSG_FIRST_LOADING_FINISHED);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDownloadFailed(MessageEvent event) {
        if (event.msg.equals("onDownloadFailed")) {
            Logger.d(LOG_TAG, "onDownloadFailed");
            showHintInDetailContainerOrToast("刷新失败，无法从网络获取全部电影信息");
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_NONE_ITEM_IN_LIST) {
                showHintInDetailContainerOrToast("电影列表暂无数据，数据正在加载中，请稍等");
                if (mProgressDialog == null) {
                    mProgressDialog = new ProgressDialog(MainActivity.this);
                    mProgressDialog.setMessage("正在加载中...");
                    mProgressDialog.setCanceledOnTouchOutside(false);
                }
                if (!mProgressDialog.isShowing()) {
                    mProgressDialog.show();
                }
            }
            if (msg.what == MSG_FIRST_LOADING_FINISHED) {
                showHintInDetailContainerOrToast("数据加载完毕，请点击海报查看电影详细信息");
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
            }
        }
    };
}

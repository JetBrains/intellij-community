package org.jetbrains.jps.api;

/**
* @author Eugene Zhuravlev
*         Date: 2/28/12
*/
public interface AsyncTaskExecutor {
  AsyncTaskExecutor DEFAULT = new AsyncTaskExecutor() {
    @Override
    public void submit(Runnable runnable) {
      new Thread(runnable).start();
    }
  };
  
  void submit(Runnable runnable);
}

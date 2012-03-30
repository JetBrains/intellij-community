package org.jetbrains.jps.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eugene Zhuravlev
 *         Date: 3/29/12
 */
public class SharedThreadPool {
  public static final ExecutorService INSTANCE = Executors.newCachedThreadPool();
  public static final AsyncTaskExecutor ASYNC_EXEC = new AsyncTaskExecutor() {
    @Override
    public void submit(Runnable runnable) {
      INSTANCE.submit(runnable);
    }
  };
}

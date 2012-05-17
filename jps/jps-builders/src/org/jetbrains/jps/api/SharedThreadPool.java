package org.jetbrains.jps.api;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 *         Date: 3/29/12
 */
public class SharedThreadPool implements Executor {
  private static final ExecutorService ourService = Executors.newCachedThreadPool();

  public static final SharedThreadPool INSTANCE = new SharedThreadPool();
  private SharedThreadPool() {
  }

  /** @noinspection MethodMayBeStatic*/
  public Future<?> submit(final Runnable task) {
    return ourService.submit(new Runnable() {
      public void run() {
        try {
          task.run();
        }
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    });
  }

  public void execute(final Runnable task) {
    ourService.execute(new Runnable() {
      public void run() {
        try {
          task.run();
        }
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    });
  }

}

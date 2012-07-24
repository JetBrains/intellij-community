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

  private static final int MAX_BUILDER_THREADS;
  static {
    int maxThreads = 4;
    try {
      maxThreads = Math.max(2, Integer.parseInt(System.getProperty(GlobalOptions.COMPILE_PARALLEL_MAX_THREADS_OPTION, "4")));
    }
    catch (NumberFormatException ignored) {
    }
    MAX_BUILDER_THREADS = maxThreads;
  }
  private static final ExecutorService ourBuilderPool = Executors.newFixedThreadPool(Math.min(MAX_BUILDER_THREADS, Math.max(2, Runtime.getRuntime().availableProcessors())));

  public static final SharedThreadPool INSTANCE = new SharedThreadPool();


  private SharedThreadPool() {
  }

  /** @noinspection MethodMayBeStatic*/
  public Future<?> submit(final Runnable task) {
    return _submit(task, ourService);
  }

  /** @noinspection MethodMayBeStatic*/
  public Future<?> submitBuildTask(final Runnable task) {
    return _submit(task, ourBuilderPool);
  }

  private static Future<?> _submit(final Runnable task, final ExecutorService service) {
    return service.submit(new Runnable() {
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

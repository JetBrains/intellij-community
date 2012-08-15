package org.jetbrains.jps.service.impl;

import org.jetbrains.jps.service.SharedThreadPool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author nik
 */
public class SharedThreadPoolImpl extends SharedThreadPool {
  private final ExecutorService myService = Executors.newCachedThreadPool();

  @Override
  public void execute(Runnable command) {
    executeOnPooledThread(command);
  }

  @Override
  public Future<?> executeOnPooledThread(final Runnable action) {
    return myService.submit(new Runnable() {
      @Override
      public void run() {
        try {
          action.run();
        }
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    });
  }
}

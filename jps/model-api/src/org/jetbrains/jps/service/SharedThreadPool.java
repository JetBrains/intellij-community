package org.jetbrains.jps.service;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * @author nik
 */
public abstract class SharedThreadPool implements Executor {
  public static SharedThreadPool getInstance() {
    return JpsServiceManager.getInstance().getService(SharedThreadPool.class);
  }

  public abstract Future<?> executeOnPooledThread(Runnable action);
}

package org.jetbrains.jps.api;

import java.util.concurrent.Executor;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/24/11
 */
public class SequentialTaskExecutor extends BoundedTaskExecutor {

  public SequentialTaskExecutor(Executor executor) {
    super(executor, 1);
  }
}

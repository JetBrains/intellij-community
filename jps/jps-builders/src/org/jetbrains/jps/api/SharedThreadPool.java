package org.jetbrains.jps.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Eugene Zhuravlev
 *         Date: 3/29/12
 */
public class SharedThreadPool {
  public static final ExecutorService INSTANCE = Executors.newCachedThreadPool();
}

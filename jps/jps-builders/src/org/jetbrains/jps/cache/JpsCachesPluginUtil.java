package org.jetbrains.jps.cache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.ExecutorService;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public final class JpsCachesPluginUtil {
  private static final Logger LOG = Logger.getInstance(JpsCachesPluginUtil.class);
  public static final String PLUGIN_NAME = "jps-cache-loader";
  public static final String INTELLIJ_REPO_NAME = "intellij.git";
  public static final ExecutorService EXECUTOR_SERVICE = AppExecutorUtil.createBoundedApplicationPoolExecutor("JpsCacheLoader Pool",
                                                                                                         INSTANCE, getThreadPoolSize());
  private JpsCachesPluginUtil() {}

  private static int getThreadPoolSize() {
    int threadsCount = Runtime.getRuntime().availableProcessors() - 1;
    LOG.info("Executor service will be configured with " + threadsCount + " threads");
    return threadsCount;
  }
}

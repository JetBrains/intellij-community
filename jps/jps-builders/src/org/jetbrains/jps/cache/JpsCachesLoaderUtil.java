package org.jetbrains.jps.cache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.ExecutorService;

import static com.intellij.execution.process.ProcessIOExecutorService.INSTANCE;

public final class JpsCachesLoaderUtil {
  private static final Logger LOG = Logger.getInstance(JpsCachesLoaderUtil.class);
  public static final String LOADER_TMP_FOLDER_NAME = "jps-cache-loader";
  public static final String INTELLIJ_REPO_NAME = "intellij.git";
  public static final ExecutorService EXECUTOR_SERVICE = AppExecutorUtil.createBoundedApplicationPoolExecutor("JpsCacheLoader Pool",
                                                                                                         INSTANCE, getThreadPoolSize());
  private JpsCachesLoaderUtil() {}

  private static int getThreadPoolSize() {
    int threadsCount = Runtime.getRuntime().availableProcessors() - 1;
    LOG.info("Executor service will be configured with " + threadsCount + " threads");
    return threadsCount;
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;

final class LoadDescriptorsContext {
  private final ExecutorService myExecutorService;
  private final PluginLoadProgressManager myPluginLoadProgressManager;

  // synchronization will ruin parallel loading, so, string pool is local per thread
  private final ThreadLocal<StringInterner> myThreadLocalStringInterner = ThreadLocal.withInitial(() -> new StringInterner());

  LoadDescriptorsContext(@Nullable PluginLoadProgressManager pluginLoadProgressManager, boolean isParallel) {
    myPluginLoadProgressManager = pluginLoadProgressManager;
    int maxThreads = isParallel ? JobSchedulerImpl.getCPUCoresCount() : 1;
    myExecutorService = maxThreads > 1 ? AppExecutorUtil.createBoundedApplicationPoolExecutor("PluginManager Loader", maxThreads) : null;
  }

  @Nullable
  ExecutorService getExecutorService() {
    return myExecutorService;
  }

  @Nullable
  PluginLoadProgressManager getPluginLoadProgressManager() {
    return myPluginLoadProgressManager;
  }

  @Nullable
  public StringInterner getStringInterner() {
    return myThreadLocalStringInterner.get();
  }
}

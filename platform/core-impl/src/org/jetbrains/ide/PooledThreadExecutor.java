/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.ide;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PooledThreadExecutor  {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.ide.PooledThreadExecutor");
  private static final AtomicInteger myAliveThreads = new AtomicInteger();
  private static final AtomicInteger seq = new AtomicInteger();
  private static final int ourReasonableThreadPoolSize = Registry.intValue("core.pooled.threads");

  private static final ExecutorService ourThreadExecutorsService = new ThreadPoolExecutor(
    3,
    Integer.MAX_VALUE,
    5 * 60L,
    TimeUnit.SECONDS,
    new SynchronousQueue<Runnable>(),
    new ThreadFactory() {
      @NotNull
      @Override
      public Thread newThread(@NotNull Runnable r) {
        final int count = myAliveThreads.incrementAndGet();
        final Thread thread = new Thread(r, "ApplicationImpl pooled thread "+seq.incrementAndGet()) {
          @Override
          public void interrupt() {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Interrupted worker, will remove from pool");
            }
            super.interrupt();
          }

          @Override
          public void run() {
            try {
              super.run();
            }
            catch (Throwable t) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("Worker exits due to exception", t);
              }
            }
            myAliveThreads.decrementAndGet();
          }
        };
        if (ApplicationInfoImpl.getShadowInstance().isEAP() && count > ourReasonableThreadPoolSize) {
          LOG.info("Not enough pooled threads; dumping threads into a file");
          PerformanceWatcher.getInstance().dumpThreads("newPooledThread/", true);
        }
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
      }
    }
  );

  private PooledThreadExecutor() {
  }

  public static final ExecutorService INSTANCE = ourThreadExecutorsService;
}
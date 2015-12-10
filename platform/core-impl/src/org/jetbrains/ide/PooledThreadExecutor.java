/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PooledThreadExecutor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.ide.PooledThreadExecutor");

  private static final AtomicInteger ourAliveThreads = new AtomicInteger();
  private static final AtomicInteger ourSeq = new AtomicInteger();
  private static final int ourReasonableThreadPoolSize = Registry.intValue("core.pooled.threads");
  private static final Lazy<Boolean> ourLogDumps = LazyKt.lazy(new Function0<Boolean>() {
    @Override
    public Boolean invoke() {
      try {
        return ApplicationInfoImpl.getShadowInstance().isEAP();
      }
      catch (Throwable ignore) {
        return false;
      }
    }
  });

  public static final ExecutorService INSTANCE = new ThreadPoolExecutor(
    3,
    Integer.MAX_VALUE,
    5 * 60L,
    TimeUnit.SECONDS,
    new SynchronousQueue<Runnable>(),
    new ThreadFactory() {
      @NotNull
      @Override
      public Thread newThread(@NotNull Runnable r) {
        final int count = ourAliveThreads.incrementAndGet();

        final Thread thread = new Thread(r, "ApplicationImpl pooled thread " + ourSeq.incrementAndGet()) {
          @Override
          public void interrupt() {
            LOG.debug("Interrupted worker, will remove from pool");
            super.interrupt();
          }

          @Override
          public void run() {
            try {
              super.run();
            }
            catch (Throwable t) {
              LOG.debug("Worker exits due to exception", t);
            }
            finally {
              ourAliveThreads.decrementAndGet();
            }
          }
        };

        if (count > ourReasonableThreadPoolSize && ourLogDumps.getValue()) {
          File file = PerformanceWatcher.getInstance().dumpThreads("newPooledThread/", true);
          LOG.info("Not enough pooled threads" + (file != null ? "; dumped threads into file '" + file.getPath() + "'" : ""));
        }

        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
      }
    }
  );

  private PooledThreadExecutor() { }
}
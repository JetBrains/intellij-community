/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.ExecutorService;

/**
 * Application tread pool.
 * This pool is<ul>
 * <li>Unbounded.</li>
 * <li>Application-wide, always active, non-shutdownable singleton.</li>
 * </ul>
 * You can use this pool for long-running and/or IO-bound tasks.
 * @see com.intellij.openapi.application.Application#executeOnPooledThread(Runnable)
 */
public final class PooledThreadExecutor {
  public static final ExecutorService INSTANCE = AppExecutorUtil.getAppExecutorService();
}
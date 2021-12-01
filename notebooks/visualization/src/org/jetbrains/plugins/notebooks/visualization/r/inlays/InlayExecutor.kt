/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays

import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise


private val inlayExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Inlay output data loader", 1)

/**
 * run [runnable] on bounded inlay thread pool backed by application thread pool
 */
fun <T> runAsyncInlay(runnable: () -> T): Promise<T> {
  val promise = AsyncPromise<T>()
  inlayExecutor.execute {
    val result = try {
      runnable()
    }
    catch (e: Throwable) {
      promise.setError(e)
      return@execute
    }
    promise.setResult(result)
  }
  return promise
}
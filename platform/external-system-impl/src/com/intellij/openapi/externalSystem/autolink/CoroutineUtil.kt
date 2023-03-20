// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal fun CoroutineScope.launch(parentDisposable: Disposable, action: suspend CoroutineScope.() -> Unit): Job {
  val disposable = Disposer.newDisposable(parentDisposable, "CoroutineUtil.launch")
  val job = launch(start = CoroutineStart.LAZY, block = action)
  job.invokeOnCompletion {
    Disposer.dispose(disposable)
  }
  Disposer.register(disposable, Disposable {
    job.cancel("disposed")
  })
  job.start()
  return job
}
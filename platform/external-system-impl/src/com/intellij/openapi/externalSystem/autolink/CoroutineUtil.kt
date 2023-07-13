// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@ApiStatus.Internal
internal fun CoroutineScope.launch(
  parentDisposable: Disposable,
  context: CoroutineContext = EmptyCoroutineContext,
  start: CoroutineStart = CoroutineStart.DEFAULT,
  block: suspend CoroutineScope.() -> Unit
): Job {
  val disposable = Disposer.newDisposable(parentDisposable, "CoroutineUtil.launch")
  val job = launch(context, start, block)
  Disposer.register(disposable, Disposable {
    job.cancel("disposed")
  })
  job.invokeOnCompletion {
    Disposer.dispose(disposable)
  }
  return job
}
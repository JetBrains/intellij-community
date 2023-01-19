// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.EmptyCoroutineContext


@ApiStatus.Internal
internal fun launch(parentDisposable: Disposable, action: suspend CoroutineScope.() -> Unit): Job {
  val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  val disposable = Disposer.newDisposable(parentDisposable, "CoroutineScope")
  val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
    disposable.use {
      action()
    }
  }
  Disposer.register(disposable, Disposable {
    job.cancel("disposed")
  })
  job.start()
  return job
}
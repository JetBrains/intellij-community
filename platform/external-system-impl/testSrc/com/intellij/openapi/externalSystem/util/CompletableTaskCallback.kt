// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.externalSystem.task.TaskCallback
import java.util.concurrent.CountDownLatch

class CompletableTaskCallback : TaskCallback {

  private val latch = CountDownLatch(1)

  override fun onSuccess() = latch.countDown()

  override fun onFailure() = latch.countDown()

  fun await() {
    latch.await()
  }
}
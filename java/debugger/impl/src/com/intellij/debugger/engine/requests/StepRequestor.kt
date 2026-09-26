// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.requests

import com.intellij.debugger.requests.Requestor

class StepRequestor(private val suspendPolicy: String, val originalRequestor: Requestor?) : SuspendingRequestor {
  override fun getSuspendPolicy(): String {
    return suspendPolicy
  }
}
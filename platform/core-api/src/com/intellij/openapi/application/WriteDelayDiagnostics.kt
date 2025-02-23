// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.application.ApplicationManager.getApplication
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object WriteDelayDiagnostics {

  fun registerWrite(waitTime: Long) = getApplication().getService(WriteDelayDiagnosticsHandler::class.java)?.registerWrite(waitTime)

  @Internal
  interface WriteDelayDiagnosticsHandler {
    fun registerWrite(waitTime: Long)
  }
}
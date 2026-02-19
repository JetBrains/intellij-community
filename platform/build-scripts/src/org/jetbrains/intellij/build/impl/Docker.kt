// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.io.runProcess

@ApiStatus.Internal
object Docker {
  val isAvailable: Boolean by lazy {
    try {
      runBlocking {
        runProcess(args = listOf("docker", "system", "info"), inheritOut = true)
      }
      true
    }
    catch (e: Exception) {
      Span.current().addEvent("Docker is not available: ${e.message}")
      false
    }
  }
}
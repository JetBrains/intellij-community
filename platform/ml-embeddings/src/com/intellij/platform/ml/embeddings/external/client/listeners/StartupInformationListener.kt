// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.external.client.listeners

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.platform.ml.embeddings.external.client.NativeServerException
import kotlinx.coroutines.CompletableDeferred

internal class StartupInformationListener : ProcessListener {
  private val portDeferred = CompletableDeferred<Int>()

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    val controlMessage = event.text.substringAfter(CONTROL_MESSAGE_PREFIX, "")
    if (controlMessage.isEmpty()) return
    val suffix = controlMessage.substringAfter(PORT_NUMBER_PREFIX).trim()
    if (suffix.isEmpty()) throw NativeServerException(IllegalStateException("Unsupported control message: ${event.text}"))
    portDeferred.complete(suffix.toInt())
  }

  suspend fun waitForTheStart(): Int = portDeferred.await()

  companion object {
    private const val CONTROL_MESSAGE_PREFIX = "[CONTROL MESSAGE]"
    private const val PORT_NUMBER_PREFIX = "PORT "
  }
}
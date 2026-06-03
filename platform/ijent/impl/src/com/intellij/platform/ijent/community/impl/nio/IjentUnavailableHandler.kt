// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration

class IjentTimeoutException(message: String) : IOException(message)

sealed class IjentUnavailableHandlerResult {
  abstract fun throwException(): Nothing
  class ProjectCloseDecision(val eelDescriptor: EelDescriptor) : IjentUnavailableHandlerResult() {
    override fun throwException(): Nothing {
      throw IjentTimeoutException("User decided to close the project without waiting for not responding ijent $eelDescriptor.")
    }
  }
  class UnrelatedIjent(val eelDescriptor: EelDescriptor) : IjentUnavailableHandlerResult() {
    override fun throwException(): Nothing {
      throw IjentTimeoutException("Target $eelDescriptor is not related to any of open projects. Not waiting for it.")
    }
  }
}

interface IjentUnavailableHandler {
  suspend fun showModalDialog(eelDescriptor: EelDescriptor): IjentUnavailableHandlerResult
  companion object {
    val EP_NAME: ExtensionPointName<IjentUnavailableHandler> = ExtensionPointName("com.intellij.project.root.unavailable")
  }
}

internal suspend fun <T> showModalDialogOnTimeout(eelDescriptor: EelDescriptor, timeout: Duration, body: suspend () -> T): T {
  // TODO behavior should depend on caller context:
  //  for EDT - basic events could be dispatched even before showing dialog.
  // TODO Now showing the dialog works only when EDT is free. In fact it's not free e.g. for DiskQueryRelay.
  if (timeout.isInfinite()) {
    return body()
  }
  return coroutineScope {
    val dialogJob = launch {
      delay(timeout)
      val ijentUnavailableHandler = IjentUnavailableHandler.EP_NAME.extensionList.singleOrNull()
      ijentUnavailableHandler?.showModalDialog(eelDescriptor)?.throwException()
    }
    try {
      body()
    }
    finally {
      dialogJob.cancel()
    }
  }
}

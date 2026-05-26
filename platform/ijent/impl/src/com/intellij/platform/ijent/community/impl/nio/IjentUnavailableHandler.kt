// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration

class IjentTimeoutException(message: String) : IOException(message)

class CloseDecision {
  fun throwException(): Nothing {
    throw IjentTimeoutException("User decided to close the project without waiting for not responding ijent.")
  }
}

interface IjentUnavailableHandler {
  suspend fun showModalDialog(): CloseDecision
  companion object {
    val EP_NAME: ExtensionPointName<IjentUnavailableHandler> = ExtensionPointName("com.intellij.project.root.unavailable")
  }
}

internal suspend fun <T> showModalDialogOnTimeout(timeout: Duration, body: suspend () -> T): T {
  // TODO behavior should depend on caller context:
  //  for EDT - basic events could be dispatched even before showing dialog
  return coroutineScope {
    val dialogJob = launch {
      delay(timeout)
      val ijentUnavailableHandler = IjentUnavailableHandler.EP_NAME.extensionList.singleOrNull()
      ijentUnavailableHandler?.showModalDialog()?.throwException()
    }
    try {
      body()
    }
    finally {
      dialogJob.cancel()
    }
  }
}

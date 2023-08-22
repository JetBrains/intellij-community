// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LOG
import com.intellij.openapi.diagnostic.ControlFlowException
import java.util.concurrent.CancellationException

internal inline fun <T> catchAndLog(file: () -> String, runnable: () -> T): T? {
  try {
    return runnable()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    if (e is ControlFlowException) {
      throw e
    }
    LOG.error("Cannot read scheme ${file()}", e)
  }
  return null
}

internal fun nameIsMissed(bytes: ByteArray): RuntimeException {
  return RuntimeException("Name is missed:\n${bytes.decodeToString()}")
}
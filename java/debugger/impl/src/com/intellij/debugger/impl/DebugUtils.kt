// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.sun.jdi.InternalException
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.VMDisconnectedException
import kotlin.coroutines.cancellation.CancellationException

internal inline fun <T, E : Exception> suppressExceptions(
  defaultValue: T?,
  rethrow: Class<E>? = null,
  supplier: () -> T?,
): T? = try {
  supplier()
}
catch (e: Throwable) {
  when (e) {
    is ProcessCanceledException, is CancellationException, is VMDisconnectedException, is ObjectCollectedException -> {
      throw e
    }
    is InternalException -> {
      fileLogger().info(e)
    }
    is Exception, is AssertionError -> {
      if (rethrow != null && rethrow.isInstance(e)) {
        throw e
      }
      fileLogger().error(e)
    }
    else -> throw e
  }
  defaultValue
}

// do not catch VMDisconnectedException
inline fun <T : Any, R> computeSafeIfAny(ep: ExtensionPointName<T>, processor: (T) -> R?): R? =
  ep.extensionList.firstNotNullOfOrNull { t ->
    try {
      processor(t)
    }
    catch (e: Exception) {
      if (e is ProcessCanceledException || e is VMDisconnectedException || e is CancellationException) {
        throw e
      }
      fileLogger().error(e)
      null
    }
  }

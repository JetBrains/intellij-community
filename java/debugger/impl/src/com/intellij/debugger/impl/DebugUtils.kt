// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.sun.jdi.InternalException
import com.sun.jdi.ObjectCollectedException
import com.sun.jdi.VMDisconnectedException
import kotlin.coroutines.cancellation.CancellationException

inline fun <T, E : Exception> suppressExceptions(
  defaultValue: T?,
  ignorePCE: Boolean,
  rethrow: Class<E>? = null,
  supplier: () -> T?,
): T? {
  try {
    return supplier()
  }
  catch (e: ProcessCanceledException) {
    if (!ignorePCE) {
      throw e
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: VMDisconnectedException) {
    throw e
  }
  catch (e: ObjectCollectedException) {
    throw e
  }
  catch (e: InternalException) {
    fileLogger().info(e)
  }
  catch (e: Exception) {
    if (rethrow != null && rethrow.isInstance(e)) {
      throw e
    }
    else {
      fileLogger().error(e)
    }
  }
  catch (e: AssertionError) {
    if (rethrow != null && rethrow.isInstance(e)) {
      throw e
    }
    else {
      fileLogger().error(e)
    }
  }
  return defaultValue
}

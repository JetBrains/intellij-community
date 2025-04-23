// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.NonExtendable
abstract class EelException @JvmOverloads constructor(
  open val error: EelError,
  cause: Throwable? = null,
) : IOException(error.toString()), EelError by error {
  init {
    cause?.let(::initCause)
  }

  class Unknown @JvmOverloads constructor(cause: Throwable? = null): EelException(EelError.Unknown, cause)
}
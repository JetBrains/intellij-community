// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.NonExtendable
@ApiStatus.Experimental
abstract class EelException(open val error: EelError) : IOException(error.toString()), EelError by error {
  constructor(error: EelError, cause: Throwable): this(error) {
    initCause(cause)
  }

  @ApiStatus.Experimental
  class Unknown : EelException {
    constructor() : super(EelError.Unknown)
    constructor(cause: Throwable): super(EelError.Unknown, cause)
  }
}
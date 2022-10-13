// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus.Internal

// used by SocketLock - do not use any platform classes or heavy JDK classes
@Internal
class CliResult(val exitCode: Int, val message: @NlsContexts.DialogMessage String?) {
  override fun toString(): String = if (message == null) exitCode.toString() else "$exitCode: $message"

  companion object {
    @JvmField
    val OK = CliResult(0, null)
  }
}
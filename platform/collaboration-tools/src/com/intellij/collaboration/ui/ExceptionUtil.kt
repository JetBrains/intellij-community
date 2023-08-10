// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.collaboration.messages.CollaborationToolsBundle
import org.jetbrains.annotations.Nls
import java.net.ConnectException
import java.nio.channels.UnresolvedAddressException

object ExceptionUtil {

  fun getPresentableMessage(exception: Throwable): @Nls String {
    if (exception.localizedMessage != null) return exception.localizedMessage

    if (exception is ConnectException) {
      if (exception.cause is UnresolvedAddressException) {
        return CollaborationToolsBundle.message("error.address.unresolved")
      }
      return CollaborationToolsBundle.message("error.connection.error")
    }
    return CollaborationToolsBundle.message("error.unknown")
  }
}
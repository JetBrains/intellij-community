// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.lang.System.Logger

fun Logger.error(message: String) {
  log(Logger.Level.ERROR, message)
}

fun Logger.info(message: String) {
  log(Logger.Level.INFO, message)
}

fun Logger.warn(message: String) {
  log(Logger.Level.WARNING, message)
}

fun Logger.debug(message: String) {
  log(Logger.Level.DEBUG, message)
}

inline fun Logger.debug(message: () -> String) {
  if (isLoggable(Logger.Level.DEBUG)) {
    log(Logger.Level.DEBUG, message())
  }
}
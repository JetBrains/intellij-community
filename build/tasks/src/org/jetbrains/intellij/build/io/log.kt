// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.lang.System.Logger
import java.util.*

fun Logger.error(message: String) {
  log(Logger.Level.ERROR, null as ResourceBundle?, message)
}

fun Logger.error(error: Throwable) {
  log(Logger.Level.ERROR, null as ResourceBundle?, error.message, error)
}

fun Logger.info(message: String) {
  log(Logger.Level.INFO, null as ResourceBundle?, message)
}

fun Logger.warn(message: String) {
  log(Logger.Level.WARNING, null as ResourceBundle?, message)
}

fun Logger.debug(message: String) {
  log(Logger.Level.DEBUG, null as ResourceBundle?, message)
}

inline fun Logger.debug(message: () -> String) {
  if (isLoggable(Logger.Level.DEBUG)) {
    log(Logger.Level.DEBUG, null as ResourceBundle?, message())
  }
}
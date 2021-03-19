// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import org.jetbrains.annotations.NonNls

inline fun Logger.debugOrInfoIfTestMode(e: Exception? = null, lazyMessage: () -> @NonNls String) {
  if (ApplicationManager.getApplication()?.isUnitTestMode == true) {
    info(lazyMessage())
  }
  else {
    debug(e, lazyMessage)
  }
}

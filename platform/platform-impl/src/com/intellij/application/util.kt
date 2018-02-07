// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application

import com.intellij.openapi.application.ex.ApplicationManagerEx

@JvmOverloads
inline fun runInAllowSaveMode(isSaveAllowed: Boolean = true, task: () -> Unit) {
  val app = ApplicationManagerEx.getApplicationEx()
  if (isSaveAllowed == app.isSaveAllowed) {
    task()
    return
  }

  app.isSaveAllowed = isSaveAllowed
  try {
    task()
  }
  finally {
    app.isSaveAllowed = !isSaveAllowed
  }
}
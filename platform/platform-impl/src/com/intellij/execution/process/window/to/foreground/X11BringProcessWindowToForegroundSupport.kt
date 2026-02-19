// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.window.to.foreground

import com.intellij.openapi.wm.impl.X11UiUtil
import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.info
import com.jetbrains.rd.util.trace

private val logger = getLogger<X11BringProcessWindowToForegroundSupport>()

internal class X11BringProcessWindowToForegroundSupport : BringProcessWindowToForegroundSupportApplicable() {
  override fun isApplicable(): Boolean {
    return StartupUiUtil.isXToolkit().also {
      if (!it)
        logger.info { "Bringing debuggee into foreground is disabled since the current machine is not running on X11 server" }
    }
  }

  override fun bring(pid: UInt): Boolean {
    val mainWindow = X11UiUtil.findVisibleWindowByPid(pid.toLong()) ?: run {
      logger.trace { "No window in \"$pid\" process found" }
      return false
    }

    return X11UiUtil.activate(mainWindow).also {
      logger.trace { "Window \"$mainWindow\" from process \"$pid\" activate is called, result : ${if (it) "success" else "fail"}" }
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.window.to.foreground

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.ui.User32Ex
import com.intellij.util.findMainWindow
import com.intellij.util.findWindowsWithText
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.trace
import com.jetbrains.rd.util.warn
import com.sun.jna.platform.win32.WinDef

private val logger = getLogger<WinBringProcessWindowToForegroundSupport>()

internal class WinBringProcessWindowToForegroundSupport : BringProcessWindowToForegroundSupport {
  override fun bring(pid: UInt): Boolean {
    val mainWindowHandle = User32Ex.INSTANCE.findMainWindow(pid) ?: run {
      logger.trace { "There's no window for \"$pid\" process" }
      return false
    }

    return User32Ex.INSTANCE.SetForegroundWindow(mainWindowHandle).also { logger.trace { "SetForegroundWindow result: $it" } }
  }

  private val windowsHandleKey = Key<MutableMap<UInt, WinDef.HWND>>("WindowsHandleKey")
  fun bringWindowWithName(pid: UInt, dataHolder: UserDataHolder, name: String): Boolean {
    val cacheMap = dataHolder.getOrCreateUserDataUnsafe(windowsHandleKey) { mutableMapOf() }

    val winHandle = dataHolder.getUserData(windowsHandleKey)?.get(pid) ?: run {
      val windows = User32Ex.INSTANCE.findWindowsWithText(pid, name)
      if (windows.size != 1) {
        logger.warn { "We found ${windows.size} windows with name \"$name\" for \"$pid\" process. Can't decide which one to bring, so skipping" }
        return false
      }
      windows.first().also { cacheMap[pid] = it }
    }

    return User32Ex.INSTANCE.SetForegroundWindow(winHandle).also { logger.trace { "SetForegroundWindow result: $it" } }
  }
}
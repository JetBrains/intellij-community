// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf

import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.WindowsRegistryUtil
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

internal abstract class SystemDarkThemeDetector {
  companion object {
    @JvmStatic val instance by lazy {
      when {
        SystemInfoRt.isMac -> MacDetector()
        SystemInfoRt.isWindows -> WindowsDetector()
        else -> DefaultDetector()
      }
    }
  }

  abstract fun check(handler: (Boolean) -> Unit)

  /**
   * The following method is executed on a polled thread. Maybe computationally intense.
   */
  protected abstract fun isDark(): Boolean

  private abstract class AsyncDetector : SystemDarkThemeDetector() {
    override fun check(handler: (Boolean) -> Unit) {
      ApplicationManager.getApplication()?.let { application ->
        application.executeOnPooledThread {
          val isDark = isDark()
          application.invokeLater { handler(isDark) }
        }
      }
    }
  }

  private class MacDetector : AsyncDetector() {
    override fun isDark(): Boolean = false
  }

  private class WindowsDetector : AsyncDetector() {
    companion object {
      const val REGISTRY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
      const val REGISTRY_VALUE = "AppsUseLightTheme"
    }

    override fun isDark(): Boolean {
      try {
        if (JnaLoader.isLoaded()) {
          return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_PATH, REGISTRY_VALUE) &&
                 Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, REGISTRY_PATH, REGISTRY_VALUE) == 0
        }
      }
      catch (e: Throwable) {}

      return WindowsRegistryUtil.readRegistryValue("HKEY_CURRENT_USER\\$REGISTRY_PATH", REGISTRY_VALUE)?.toInt() == 0
    }
  }

  private class DefaultDetector : SystemDarkThemeDetector() {
    override fun isDark(): Boolean = false
    override fun check(handler: (Boolean) -> Unit) {}
  }
}
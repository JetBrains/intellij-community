// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.WindowsRegistryUtil

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
      const val REGISTRY_PATH = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
      const val REGISTRY_VALUE = "AppsUseLightTheme"
    }

    override fun isDark(): Boolean {
      val dark = WindowsRegistryUtil.readRegistryValue(REGISTRY_PATH, REGISTRY_VALUE)
      return dark?.toBoolean() ?: false
    }
  }

  private class DefaultDetector : SystemDarkThemeDetector() {
    override fun isDark(): Boolean = false
    override fun check(handler: (Boolean) -> Unit) {}
  }
}
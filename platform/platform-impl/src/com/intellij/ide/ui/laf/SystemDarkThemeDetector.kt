// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf

import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.foundation.Foundation
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.jetbrains.annotations.NonNls
import java.util.function.Consumer

internal abstract class SystemDarkThemeDetector {
  companion object {
    @JvmStatic val instance by lazy {
      when {
        SystemInfo.isMacOSMojave -> MacOSDetector()
        SystemInfo.isWindows -> WindowsDetector()
        else -> EmptyDetector()
      }
    }
  }

  abstract fun check(handler: Consumer<Boolean>)

  /**
   * The following method is executed on a polled thread. Maybe computationally intense.
   */
  protected abstract fun isDark(): Boolean

  abstract val detectionSupported : Boolean

  private abstract class AsyncDetector : SystemDarkThemeDetector() {
    override fun check(handler: Consumer<Boolean>) {
      ApplicationManager.getApplication()?.let { application ->
        application.executeOnPooledThread {
          val isDark = isDark()
          application.invokeLater { handler.accept(isDark) }
        }
      }
    }
  }

  private class MacOSDetector (override val detectionSupported: Boolean = JnaLoader.isLoaded() && SystemInfo.isMacOSMojave)
      : AsyncDetector() {
    companion object {
      const val AQUA_THEME_NAME      = "NSAppearanceNameAqua"
      const val DARK_AQUA_THEME_NAME = "NSAppearanceNameDarkAqua"
    }

    override fun isDark(): Boolean {
      val pool = Foundation.NSAutoreleasePool()
      try {
        val appearanceID = Foundation.invoke(Foundation.invoke("NSApplication", "sharedApplication"), "effectiveAppearance")
        val appearanceName = Foundation.invoke(appearanceID, "bestMatchFromAppearancesWithNames",
                          Foundation.NSArray.createArrayOfStrings(AQUA_THEME_NAME, DARK_AQUA_THEME_NAME))
        val ret = Foundation.invoke(appearanceName, "isEqualToString", Foundation.nsString(DARK_AQUA_THEME_NAME))
        return ret.toInt() == 0
      }
      finally{
        pool.drain()
      }
    }
  }

  private class WindowsDetector (override val detectionSupported: Boolean = JnaLoader.isLoaded() && SystemInfo.isWin10OrNewer)
      : AsyncDetector() {
    companion object {
      @NonNls const val REGISTRY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
      @NonNls const val REGISTRY_VALUE = "AppsUseLightTheme"
    }

    override fun isDark(): Boolean {
      try {
        return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REGISTRY_PATH, REGISTRY_VALUE) &&
               Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, REGISTRY_PATH, REGISTRY_VALUE) == 0
      }
      catch (e: Throwable) {}

      return false
    }
  }

  private class EmptyDetector (override val detectionSupported: Boolean = false) : SystemDarkThemeDetector() {
    override fun isDark(): Boolean = false
    override fun check(handler: Consumer<Boolean>) {}
  }
}
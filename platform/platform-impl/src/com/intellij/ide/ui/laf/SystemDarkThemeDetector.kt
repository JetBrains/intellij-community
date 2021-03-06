// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf

import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.util.concurrency.NonUrgentExecutor
import com.sun.jna.Callback
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.jetbrains.annotations.NonNls
import java.awt.Toolkit
import java.beans.PropertyChangeEvent
import java.util.function.Consumer

sealed class SystemDarkThemeDetector {
  companion object {
    @JvmStatic
    fun createDetector(syncFunction: Consumer<Boolean>): SystemDarkThemeDetector {
      return when {
        SystemInfo.isMacOSMojave -> MacOSDetector(syncFunction)
        SystemInfo.isWin10OrNewer -> WindowsDetector(syncFunction)
        else -> EmptyDetector()
      }
    }
  }

  abstract fun check()

  /**
   * The following method is executed on a polled thread. Maybe computationally intense.
   */
  abstract fun isDark(): Boolean

  abstract val detectionSupported: Boolean
}

private abstract class AsyncDetector : SystemDarkThemeDetector() {
  abstract val syncFunction: Consumer<Boolean>

  override fun check() {
    NonUrgentExecutor.getInstance().execute {
      val isDark = isDark()
      ApplicationManager.getApplication().invokeLater(Runnable { syncFunction.accept(isDark) }, ModalityState.any())
    }
  }
}

private class MacOSDetector(override val syncFunction: Consumer<Boolean>) : AsyncDetector() {
  override val detectionSupported: Boolean
    get() = SystemInfo.isMacOSMojave && JnaLoader.isLoaded()

  val themeChangedCallback = object : Callback {
    @Suppress("unused")
    fun callback() { // self: ID, selector: Pointer, id: ID
      check()
    }
  }

  init {
    val pool = Foundation.NSAutoreleasePool()
    try {
        val delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSColorChangesObserver")
        if (ID.NIL != delegateClass) {
          if (!Foundation.addMethod(delegateClass, Foundation.createSelector("handleAppleThemeChanged:"), themeChangedCallback, "v@")) {
            throw RuntimeException("Cannot add observer method")
          }
          Foundation.registerObjcClassPair(delegateClass)
        }

        val delegate = Foundation.invoke("NSColorChangesObserver", "new")
        Foundation.invoke(Foundation.invoke("NSDistributedNotificationCenter", "defaultCenter"), "addObserver:selector:name:object:",
                          delegate,
                          Foundation.createSelector("handleAppleThemeChanged:"),
                          Foundation.nsString("AppleInterfaceThemeChangedNotification"),
                          ID.NIL)
    }
    finally {
      pool.drain()
    }
  }

  override fun isDark(): Boolean {
    val pool = Foundation.NSAutoreleasePool()
    try { // https://developer.apple.com/forums/thread/118974
      val userDefaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
      val appleInterfaceStyle = Foundation.toStringViaUTF8(Foundation.invoke(userDefaults, "objectForKey:", Foundation.nsString("AppleInterfaceStyle")))

      //val autoMode = SystemInfo.isMacOSCatalina &&
      //               Foundation.invoke(userDefaults, "boolForKey:", Foundation.nsString("AppleInterfaceStyleSwitchesAutomatically")).booleanValue()

      return appleInterfaceStyle?.toLowerCase()?.contains("dark") ?: false
    }
    finally{
      pool.drain()
    }
  }
}

private class WindowsDetector(override val syncFunction: Consumer<Boolean>) : AsyncDetector() {
  override val detectionSupported: Boolean
    get() = SystemInfo.isWin10OrNewer && JnaLoader.isLoaded()

  companion object {
    @NonNls const val REGISTRY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    @NonNls const val REGISTRY_VALUE = "AppsUseLightTheme"
  }

  init {
    Toolkit.getDefaultToolkit().addPropertyChangeListener("win.lightTheme.on") { e: PropertyChangeEvent ->
      syncFunction.accept(e.newValue != java.lang.Boolean.TRUE)
    }
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

private class EmptyDetector : SystemDarkThemeDetector() {
  override val detectionSupported = false
  override fun isDark() = false
  override fun check() {}
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.intellij.util.system.WindowsRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Toolkit
import java.beans.PropertyChangeEvent
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandles
import java.util.Locale
import java.util.UUID
import java.util.function.BiConsumer

@ApiStatus.Internal
fun isSystemThemeDark(): Boolean? = SystemDarkThemeDetector.createParametrizedDetector(null).isDark()

@ApiStatus.Internal
sealed class SystemDarkThemeDetector {
  @ApiStatus.Internal
  companion object {
    fun createParametrizedDetector(syncFunction: BiConsumer<Boolean, Boolean?>?): SystemDarkThemeDetector {
      return when {
        SystemInfoRt.isMac -> MacOSDetector(syncFunction)
        SystemInfo.isWin10OrNewer -> WindowsDetector(syncFunction)
        SystemInfoRt.isLinux -> LinuxThemeDetector(syncFunction)
        else -> EmptyDetector
      }
    }
  }

  fun check() { check(null) }

  abstract fun check(parameter: Boolean?)

  /**
   * The following method is executed on a polled thread. Maybe computationally intense.
   *
   * @return the detected state, or `null` if it can't be detected (because of an error or lack of environment support)
   */
  abstract fun isDark(): Boolean?

  abstract val detectionSupported: Boolean
}

private abstract class AsyncDetector : SystemDarkThemeDetector() {
  abstract val syncFunction: BiConsumer<Boolean, Boolean?>?

  override fun check(parameter: Boolean?) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      RegistryManager.getInstance()
      val isDark = isDark() ?: return@launch
      withContext(Dispatchers.UiWithModelAccess + ModalityState.any().asContextElement()) {
        syncFunction?.accept(isDark, parameter)
      }
    }
  }
}

private class MacOSDetector(override val syncFunction: BiConsumer<Boolean, Boolean?>?) : AsyncDetector() {
  override val detectionSupported: Boolean
    get() = SystemInfoRt.isMac && Foundation.isAvailable()

  val themeChangedCallback = object {
    @Suppress("unused")
    fun callback() {
      check(null)
    }
  }

  init {
    val pool = Foundation.NSAutoreleasePool()
    try {
      val selector = if (useAppearanceApi()) Foundation.createSelector("observeValueForKeyPath:ofObject:change:context:")
      else Foundation.createSelector("handleAppleThemeChanged:")

      val className = "NSColorChangesObserver_" + UUID.randomUUID().toString().replace("-", "")
      val delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), className)
      val callback = MethodHandles.dropArguments(Foundation.callback(themeChangedCallback, "callback"), 0,
                                                 List(if (useAppearanceApi()) 6 else 3) { MemorySegment::class.java })

      if (ID.NIL != delegateClass) {
        if (!Foundation.addMethod(delegateClass, selector, callback, if (useAppearanceApi()) "v@:@@@^v" else "v@:@")) {
          throw RuntimeException("Cannot add observer method")
        }
        Foundation.registerObjcClassPair(delegateClass)
      }

      val delegate = Foundation.invoke(delegateClass, "new")

      if (useAppearanceApi()) {
        val app = Foundation.invoke("NSApplication", "sharedApplication")
        Foundation.invoke(app, "addObserver:forKeyPath:options:context:", delegate, Foundation.nsString("effectiveAppearance"),
                          0x01 /*NSKeyValueObservingOptionNew*/, ID.NIL)
      }
      else {
        Foundation.invoke(Foundation.invoke("NSDistributedNotificationCenter", "defaultCenter"), "addObserver:selector:name:object:",
                          delegate,
                          selector,
                          Foundation.nsString("AppleInterfaceThemeChangedNotification"),
                          ID.NIL)
      }
    }
    finally {
      pool.drain()
    }
  }

  override fun isDark(): Boolean {
    val pool = Foundation.NSAutoreleasePool()
    try {
      if (useAppearanceApi()) {
        val app = Foundation.invoke("NSApplication", "sharedApplication")
        val name = Foundation.toStringViaUTF8(Foundation.invoke(Foundation.invoke(app, "effectiveAppearance"), "name"))
        return name?.equals("NSAppearanceNameDarkAqua") ?: false
      }

      // https://developer.apple.com/forums/thread/118974
      val userDefaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults")
      val appleInterfaceStyle = Foundation.toStringViaUTF8(Foundation.invoke(userDefaults, "objectForKey:", Foundation.nsString("AppleInterfaceStyle")))

      return appleInterfaceStyle?.lowercase(Locale.getDefault())?.contains("dark") ?: false
    }
    finally{
      pool.drain()
    }
  }

  private fun useAppearanceApi() = SystemInfo.isMacOSCatalina && "system".equals(System.getProperty("apple.awt.application.appearance"), true)
}

private class WindowsDetector(override val syncFunction: BiConsumer<Boolean, Boolean?>?) : AsyncDetector() {
  override val detectionSupported: Boolean
    get() = SystemInfo.isWin10OrNewer

  companion object {
    @NonNls const val REGISTRY_PATH = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    @NonNls const val REGISTRY_VALUE = "AppsUseLightTheme"
  }

  init {
    Toolkit.getDefaultToolkit().addPropertyChangeListener("win.lightTheme.on") { e: PropertyChangeEvent ->
      ApplicationManager.getApplication().invokeLater({ syncFunction?.accept(e.newValue != true, null) }, ModalityState.any())
    }
  }

  override fun isDark(): Boolean {
    try {
      return WindowsRegistry.getInt(WindowsRegistry.Hive.CURRENT_USER, REGISTRY_PATH, REGISTRY_VALUE) == 0
    }
    catch (_: Throwable) {}
    return false
  }
}

private class LinuxThemeDetector(override val syncFunction: BiConsumer<Boolean, Boolean?>?) : AsyncDetector() {

  private val service: DBusSettingsMonitorService
    get() = service()

  override val detectionSupported: Boolean
    get() {
      return service.isServiceAllowed && isDark() != null
    }

  init {
    service.setDarkSchemeListener {
      syncFunction?.accept(it, null)
    }
  }

  override fun isDark(): Boolean? {
    return service.darkScheme.value
  }

}

private object EmptyDetector : SystemDarkThemeDetector() {
  override val detectionSupported: Boolean
    get() = false

  override fun isDark() = false
  override fun check(parameter: Boolean?) {}
}

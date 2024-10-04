// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.ide.GeneralSettings
import com.intellij.ide.isSupportScreenReadersOverridden
import com.intellij.ide.ui.laf.setEarlyUiLaF
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.Foundation.NSAutoreleasePool
import com.intellij.ui.mac.foundation.ID
import com.intellij.util.User32Ex
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.accessibility.AccessibleRole

object AccessibilityUtils {
  @JvmField
  val GROUPED_ELEMENTS: AccessibleRole = if (SystemInfoRt.isMac) AccessibleRole.AWT_COMPONENT else AccessibleRole.PANEL
}

@ApiStatus.Internal
suspend fun enableScreenReaderSupportIfNecessary() {
  if (isSupportScreenReadersOverridden()) {
    AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_SUPPORT_ENABLED_VM)
    return
  }

  if (!isScreenReaderDetected()) {
    return
  }

  AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_DETECTED)
  val appName = ApplicationInfoImpl.getShadowInstance().versionName
  val answer = withContext(RawSwingDispatcher) {
    setEarlyUiLaF()

    MessageDialogBuilder.yesNo(title = ApplicationBundle.message("title.screen.reader.support"),
                               message = ApplicationBundle.message("confirmation.screen.reader.enable", appName))
      .yesText(ApplicationBundle.message("button.enable"))
      .noText(Messages.getCancelButton())
      .ask(null as Component?)
  }
  if (answer) {
    AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_SUPPORT_ENABLED)
    enable = true
  }
}

@Volatile
private var enable = false

private fun isScreenReaderDetected(): Boolean {
  return when {
    SystemInfoRt.isWindows -> isWindowsScreenReaderEnabled()
    SystemInfoRt.isMac -> isMacVoiceOverEnabled()
    else -> false
  }
}

/*
 * get macOS NSWorkspace.shared.isVoiceOverEnabled property (https://developer.apple.com/documentation/devicemanagement/accessibility)
 */
private fun isMacVoiceOverEnabled(): Boolean {
  val pool = NSAutoreleasePool()
  var universalAccess: ID? = null
  try {
    universalAccess = Foundation.invoke(
      Foundation.invoke("NSUserDefaults", "alloc"),
      "initWithSuiteName:",
      Foundation.nsString("com.apple.universalaccess")
    )
    val voiceOverEnabledKey = Foundation.invoke(universalAccess, "boolForKey:", Foundation.nsString("voiceOverOnOffKey"))
    return voiceOverEnabledKey.booleanValue()
  }
  finally {
    if (universalAccess != null) {
      Foundation.cfRelease(universalAccess)
    }
    pool.drain()
  }
}

/*
 * get Windows SPI_GETSCREENREADER system parameter
 * https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-systemparametersinfoa#SPI_GETSCREENREADER
 */
private fun isWindowsScreenReaderEnabled(): Boolean {
  val isActive = WinDef.BOOLByReference()
  val retValue = User32Ex.INSTANCE.SystemParametersInfo(WinDef.UINT(0x0046), WinDef.UINT(0), isActive, WinDef.UINT(0))
  return retValue && isActive.value.booleanValue()
}

@ApiStatus.Internal
suspend fun enableScreenReaderSupportIfNeeded() {
  if (enable) {
    serviceAsync<GeneralSettings>().isSupportScreenReaders = true
  }
}
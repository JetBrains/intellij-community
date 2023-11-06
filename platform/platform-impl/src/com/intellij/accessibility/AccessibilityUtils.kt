// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.Foundation.NSAutoreleasePool
import com.intellij.ui.mac.foundation.ID
import com.intellij.util.User32Ex
import com.sun.jna.platform.win32.WinDef
import javax.accessibility.AccessibleRole

object AccessibilityUtils {
  @JvmField
  val GROUPED_ELEMENTS: AccessibleRole = if (SystemInfoRt.isMac) AccessibleRole.AWT_COMPONENT else AccessibleRole.PANEL
}

internal fun enableScreenReaderSupportIfNecessary() {
  if (GeneralSettings.isSupportScreenReadersOverridden()) {
    AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_SUPPORT_ENABLED_VM)
    return
  }

  if (isScreenReaderDetected()) {
    AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_DETECTED)
    val appName = ApplicationInfoImpl.getShadowInstance().versionName
    val answer = Messages.showYesNoDialog(ApplicationBundle.message("confirmation.screen.reader.enable", appName),
                                          ApplicationBundle.message("title.screen.reader.support"),
                                          ApplicationBundle.message("button.enable"), Messages.getCancelButton(),
                                          Messages.getQuestionIcon())
    if (answer == Messages.YES) {
      AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_SUPPORT_ENABLED)
      enable = true
    }
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

private class EnableScreenReaderSupportTask : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (enable) {
      serviceAsync<GeneralSettings>().isSupportScreenReaders = true
    }
  }
}
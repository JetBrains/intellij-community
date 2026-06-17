// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.GeneralSettings
import com.intellij.ide.isSupportScreenReadersOverridden
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.impl.LinuxUiUtil.isGnomeScreenReaderSettingEnabled
import com.intellij.openapi.wm.impl.LinuxUiUtil.isGnomeZoomEnabled
import com.intellij.openapi.wm.impl.LinuxUiUtil.isOrcaProcessRunning
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.accessibility.ScreenReader.ASSISTIVE_TECHNOLOGIES_PROPERTY
import com.intellij.util.ui.accessibility.ScreenReader.ATK_WRAPPER
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<LinuxAccessibilitySupport>()

private enum class LinuxA11yChoice(private val dialogCode: Int) {
  Cancel(0),
  EnableAtkWrapper(1),
  EnableAtkWrapperAndScreenReader(2);

  fun toDialogCode(): Int = dialogCode

  companion object {
    fun fromDialogCode(code: Int): LinuxA11yChoice {
      return entries.firstOrNull { it.dialogCode == code } ?: Cancel
    }

    fun fromDialogResult(exitCode: Int, enableAccessibilityExitCode: Int, isScreenReaderCheckboxSelected: Boolean): LinuxA11yChoice {
      return when {
        exitCode != enableAccessibilityExitCode -> Cancel
        isScreenReaderCheckboxSelected -> EnableAtkWrapperAndScreenReader
        else -> EnableAtkWrapper
      }
    }
  }
}

@ApiStatus.Internal
object LinuxAccessibilitySupport {
  private const val ENABLE_ACCESSIBILITY_BUTTON_INDEX = 0

  @Volatile
  private var screenReaderSupportRequested = false

  @Volatile
  private var linuxAccessibilitySupportRequested = false

  @Volatile
  private var atkWrapperActivatedInCurrentSession = false

  @Volatile
  private var atkWrapperEnabledInConfig = false

  @Volatile
  private var screenReaderDetected = false

  @Volatile
  private var magnifierDetected = false

  fun enableLinuxAtkWrapper() {
    screenReaderDetected = isLinuxScreenReaderEnabled() || System.getProperty("force.screen.reader.detection").toBoolean()
    magnifierDetected = isLinuxScreenMagnifierEnabled()
    atkWrapperEnabledInConfig = isAtkWrapperEnabled()
    if ((isSupportScreenReadersOverridden() || screenReaderDetected || magnifierDetected) && !atkWrapperEnabledInConfig) {
      configureAndTryActivateLinuxAtkWrapper()
    }

    if (linuxAccessibilitySupportRequested) {
      AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.LINUX_ACCESSIBILITY_SUPPORT_ENABLED)
    }
  }


  fun showLinuxAccessibilityDialog() {
    if (isSupportScreenReadersOverridden()) {
      AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_SUPPORT_ENABLED_VM)

      if (atkWrapperEnabledInConfig) {
        return
      }
      // If Java ATK Wrapper is not enabled, continue and suggest enabling it
      // when a screen reader or magnifier is detected.
    }

    if (!screenReaderDetected && !magnifierDetected) {
      return
    }

    if (screenReaderDetected) {
      AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_DETECTED)
    }

    when (askToEnableLinuxAccessibilitySupport(screenReaderDetected)) {
      LinuxA11yChoice.EnableAtkWrapper -> {
        linuxAccessibilitySupportRequested = true
      }
      LinuxA11yChoice.EnableAtkWrapperAndScreenReader -> {
        linuxAccessibilitySupportRequested = true
        screenReaderSupportRequested = true
      }
      LinuxA11yChoice.Cancel -> Unit
    }

    if (linuxAccessibilitySupportRequested) {
      AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.LINUX_ACCESSIBILITY_SUPPORT_ENABLED)
    }

    if (screenReaderSupportRequested && !isSupportScreenReadersOverridden()) {
      AccessibilityUsageTrackerCollector.featureTriggered(AccessibilityUsageTrackerCollector.SCREEN_READER_SUPPORT_ENABLED)
    }
  }

  /**
   * Returns true if the IDE should be restarted either to apply Java ATK Wrapper VM option changes
   * or to leave the temporary runtime ATK Wrapper activation state.
   */
  suspend fun applyRequestedChanges(): Boolean {
    if (screenReaderSupportRequested && !isSupportScreenReadersOverridden()) {
      serviceAsync<GeneralSettings>().isSupportScreenReaders = true
    }

    val vmOptionsUpdated = linuxAccessibilitySupportRequested && !atkWrapperEnabledInConfig && runCatching {
      VMOptions.setProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY, ATK_WRAPPER)
    }.onFailure {
      LOG.warn("Failed to update custom VM options for Java ATK Wrapper support. " +
               "Could not persist '$ASSISTIVE_TECHNOLOGIES_PROPERTY=$ATK_WRAPPER'.", it)
    }.isSuccess

    return atkWrapperActivatedInCurrentSession || vmOptionsUpdated
  }

  fun isLinuxScreenReaderEnabled(): Boolean {
    return isGnomeScreenReaderSettingEnabled() || isOrcaProcessRunning()
  }

  private fun isLinuxScreenMagnifierEnabled(): Boolean {
    return isGnomeZoomEnabled()
  }

  private fun isAtkWrapperEnabled(): Boolean {
    return System.getProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY)?.contains(ATK_WRAPPER) == true ||
           ScreenReader.isEnabled(ATK_WRAPPER)
  }

  private fun askToEnableLinuxAccessibilitySupport(
    isScreenReaderDetected: Boolean,
  ): LinuxA11yChoice {
    val dialogResultCode = Messages.showCheckboxMessageDialog(
      ApplicationBundle.message("confirmation.linux.accessibility.enable", ApplicationInfoImpl.getShadowInstance().versionName),
      ApplicationBundle.message("title.linux.accessibility.support"),
      arrayOf(
        ApplicationBundle.message("button.enable.linux.accessibility.support"),
        Messages.getCancelButton(),
      ),
      ApplicationBundle.message("checkbox.enable.linux.screen.reader.support"),
      isScreenReaderDetected,
      ENABLE_ACCESSIBILITY_BUTTON_INDEX,
      ENABLE_ACCESSIBILITY_BUTTON_INDEX,
      Messages.getQuestionIcon(),
    ) { exitCode, checkbox ->
      LinuxA11yChoice.fromDialogResult(exitCode, ENABLE_ACCESSIBILITY_BUTTON_INDEX, checkbox.isSelected).toDialogCode()
    }

    return LinuxA11yChoice.fromDialogCode(dialogResultCode)
  }

  /**
   * Sets Java ATK Wrapper as the assistive technology, and tries to activate it for the current session.
   */
  private fun configureAndTryActivateLinuxAtkWrapper() {
    System.setProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY, ATK_WRAPPER)

    atkWrapperActivatedInCurrentSession = runCatching {
      val assistiveTechnologyClass = Class.forName(ATK_WRAPPER, false, ClassLoader.getSystemClassLoader())
      assistiveTechnologyClass.getConstructor().newInstance()
    }.onFailure {
      LOG.warn("Failed to activate Java ATK Wrapper for the current IDE session. " +
               "The '$ASSISTIVE_TECHNOLOGIES_PROPERTY' system property was set to '$ATK_WRAPPER', " +
               "but runtime activation failed.", it)
    }.isSuccess
  }
}

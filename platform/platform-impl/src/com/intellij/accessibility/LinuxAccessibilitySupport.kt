// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.accessibility

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.GeneralSettings
import com.intellij.ide.isSupportScreenReadersOverridden
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.impl.LinuxUiUtil.isGnomeScreenReaderSettingEnabled
import com.intellij.openapi.wm.impl.LinuxUiUtil.isGnomeZoomEnabled
import com.intellij.openapi.wm.impl.LinuxUiUtil.isOrcaProcessRunning
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.accessibility.ScreenReader.ASSISTIVE_TECHNOLOGIES_PROPERTY
import com.intellij.util.ui.accessibility.ScreenReader.ATK_WRAPPER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<LinuxAccessibilitySupport>()

private enum class LinuxA11yChoice(private val dialogCode: Int) {
  CANCEL(0),
  ENABLE_ATK_WRAPPER(1),
  ENABLE_ATK_WRAPPER_AND_SCREEN_READER(2);

  fun toDialogCode(): Int = dialogCode

  companion object {
    fun fromDialogCode(code: Int): LinuxA11yChoice =
      entries.firstOrNull { it.dialogCode == code } ?: CANCEL

    fun fromDialogResult(exitCode: Int, enableAccessibilityExitCode: Int, isScreenReaderCheckboxSelected: Boolean): LinuxA11yChoice {
      return when {
        exitCode != enableAccessibilityExitCode -> CANCEL
        isScreenReaderCheckboxSelected -> ENABLE_ATK_WRAPPER_AND_SCREEN_READER
        else -> ENABLE_ATK_WRAPPER
      }
    }
  }
}

@ApiStatus.Internal
object LinuxAccessibilitySupport {
  private const val ENABLE_ACCESSIBILITY_BUTTON_INDEX = 0
  private const val ASSISTIVE_TECHNOLOGIES_VM_OPTION_PREFIX = "-D$ASSISTIVE_TECHNOLOGIES_PROPERTY="
  private const val FORCE_SCREEN_READER_DETECTION_PROPERTY = "force.screen.reader.detection"

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

  fun detectAndConfigureLinuxAtkWrapper() {
    screenReaderDetected = isLinuxScreenReaderEnabled() || System.getProperty(FORCE_SCREEN_READER_DETECTION_PROPERTY).toBoolean()
    magnifierDetected = isLinuxScreenMagnifierEnabled()
    atkWrapperEnabledInConfig = isAtkWrapperEnabled()
    if ((isSupportScreenReadersOverridden() || screenReaderDetected || magnifierDetected) && !atkWrapperEnabledInConfig) {
      configureAndTryActivateLinuxAtkWrapper()
    }
  }

  suspend fun showLinuxAccessibilityDialog() {
    if (isSupportScreenReadersOverridden()) {
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
      LinuxA11yChoice.ENABLE_ATK_WRAPPER -> {
        linuxAccessibilitySupportRequested = true
      }
      LinuxA11yChoice.ENABLE_ATK_WRAPPER_AND_SCREEN_READER -> {
        linuxAccessibilitySupportRequested = true
        screenReaderSupportRequested = true
      }
      LinuxA11yChoice.CANCEL -> Unit
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

    val vmOptionsUpdated = linuxAccessibilitySupportRequested &&
                           !atkWrapperEnabledInConfig &&
                           updateAtkWrapperVmOption(shouldEnableAtkWrapper = true)

    val restartRequired = atkWrapperActivatedInCurrentSession || vmOptionsUpdated
    if (restartRequired) {
      AccessibilityUsageTrackerCollector.flushRaisedEvents()
    }

    return restartRequired
  }

  @JvmStatic
  fun syncAtkWrapperVmOption(isScreenReaderSupportEnabled: Boolean) {
    updateAtkWrapperVmOption(shouldEnableAtkWrapper = isScreenReaderSupportEnabled)
  }

  private fun updateAtkWrapperVmOption(shouldEnableAtkWrapper: Boolean): Boolean {
    if (!SystemInfoRt.isLinux || !VMOptions.canWriteOptions()) {
      return false
    }

    val currentOptionValue = VMOptions.readOption(ASSISTIVE_TECHNOLOGIES_VM_OPTION_PREFIX, false)
    val fallbackOptionValue = VMOptions.readOption(ASSISTIVE_TECHNOLOGIES_VM_OPTION_PREFIX, true)
    val newOptionValue = computeUpdatedAssistiveTechnologiesOptionValue(
      currentOptionValue = currentOptionValue,
      fallbackOptionValue = fallbackOptionValue,
      shouldEnableAtkWrapper = shouldEnableAtkWrapper,
    )

    return newOptionValue != currentOptionValue && runCatching {
      VMOptions.setProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY, newOptionValue)
    }.onFailure {
      LOG.warn("Failed to update custom VM options for Java ATK Wrapper support. " +
               "Could not persist '$ASSISTIVE_TECHNOLOGIES_PROPERTY'.", it)
    }.isSuccess
  }

  fun isLinuxScreenReaderEnabled(): Boolean {
    return isGnomeScreenReaderSettingEnabled() || isOrcaProcessRunning()
  }

  private fun computeUpdatedAssistiveTechnologiesOptionValue(
    currentOptionValue: String?,
    fallbackOptionValue: String?,
    shouldEnableAtkWrapper: Boolean,
  ): String? {
    val fallbackAssistiveTechnologies = parseAssistiveTechnologiesOptionValue(fallbackOptionValue)

    val updatedAssistiveTechnologies = parseAssistiveTechnologiesOptionValue(currentOptionValue ?: fallbackOptionValue)
    if (shouldEnableAtkWrapper) {
      updatedAssistiveTechnologies.add(ATK_WRAPPER)
    }
    else {
      updatedAssistiveTechnologies.remove(ATK_WRAPPER)
    }

    if (currentOptionValue == null && updatedAssistiveTechnologies == fallbackAssistiveTechnologies) {
      return null
    }

    return when {
      updatedAssistiveTechnologies.isNotEmpty() -> updatedAssistiveTechnologies.joinToString(",")
      fallbackOptionValue != null -> ""
      else -> null
    }
  }

  private fun isLinuxScreenMagnifierEnabled(): Boolean {
    return isGnomeZoomEnabled()
  }

  /**
   * `javax.accessibility.assistive_technologies` is comma-delimited list (https://docs.oracle.com/en/java/javase/21/access/accessibility-properties.html)
   */
  private fun parseAssistiveTechnologiesOptionValue(optionValue: String?): LinkedHashSet<String> {
    return optionValue
             ?.split(',')
             ?.asSequence()
             ?.map(String::trim)
             ?.filter(String::isNotEmpty)
             ?.toCollection(LinkedHashSet())
           ?: LinkedHashSet()
  }

  private fun isAtkWrapperEnabled(): Boolean {
    return ATK_WRAPPER in parseAssistiveTechnologiesOptionValue(System.getProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY)) ||
           ScreenReader.isEnabled(ATK_WRAPPER)
  }

  private suspend fun askToEnableLinuxAccessibilitySupport(
    isScreenReaderDetected: Boolean,
  ): LinuxA11yChoice {
    val dialogResultCode = withContext(Dispatchers.EDT) {
      Messages.showCheckboxMessageDialog(
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
    }

    return LinuxA11yChoice.fromDialogCode(dialogResultCode)
  }

  /**
   * Ensures Java ATK Wrapper is configured in assistive technologies, and tries to activate it for the current session.
   */
  private fun configureAndTryActivateLinuxAtkWrapper() {
    val currentPropertyValue = System.getProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY)
    val fallbackPropertyValue = VMOptions.readOption(ASSISTIVE_TECHNOLOGIES_VM_OPTION_PREFIX, true)

    val assistiveTechnologies = parseAssistiveTechnologiesOptionValue(currentPropertyValue ?: fallbackPropertyValue)
    assistiveTechnologies.add(ATK_WRAPPER)

    val updatedOptionValue = assistiveTechnologies.joinToString(",")
    System.setProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY, updatedOptionValue)

    atkWrapperActivatedInCurrentSession = runCatching {
      val assistiveTechnologyClass = Class.forName(ATK_WRAPPER, false, ClassLoader.getSystemClassLoader())
      assistiveTechnologyClass.getConstructor().newInstance()
    }.onFailure {
      LOG.warn("Failed to activate Java ATK Wrapper for the current IDE session. " +
               "The '$ASSISTIVE_TECHNOLOGIES_PROPERTY' system property was set to '$updatedOptionValue', " +
               "but runtime activation failed.", it)
    }.isSuccess
  }
}

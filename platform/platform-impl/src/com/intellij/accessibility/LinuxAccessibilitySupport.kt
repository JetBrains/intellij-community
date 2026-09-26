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
    val vmOptionsUpdated = linuxAccessibilitySupportRequested &&
                           !atkWrapperEnabledInConfig &&
                           updateAtkWrapperVmOption(shouldEnableAtkWrapper = true)

    if (screenReaderSupportRequested && !isSupportScreenReadersOverridden()) {
      serviceAsync<GeneralSettings>().isSupportScreenReaders = true
    }

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

  /**
   * Updates the `javax.accessibility.assistive_technologies` VM option in the user's .vmoptions file
   * (see [VMOptions.getUserOptionsFile]). By contract, the product ships no such option in
   * `bin/<product>64.vmoptions`, so the user's file is the only .vmoptions file that can declare it, and the value
   * read here always comes from the same file the value is written to.
   *
   * That VM option is not the only way the Java ATK Wrapper can be turned on: it may also be declared in
   * `~/.accessibility.properties` or `<jre>/conf/accessibility.properties`. The IDE never writes those files, so
   * [ATK_WRAPPER] normally does not appear there unless it was set up outside the IDE. The JVM prefers the
   * system property over both files; see the priority list in [ScreenReader.isEnabled].
   *
   * Turning the wrapper off deletes the option instead of writing an empty value on purpose. An empty value wins
   * over both properties files, so it would also switch off everything else declared there - technologies the IDE
   * neither knows about nor put there. Deleting the option keeps such a deliberate setup working, at the price of
   * the JVM falling back to it: when a properties file declares [ATK_WRAPPER], the wrapper stays enabled until the
   * user removes it there as well.
   *
   * Known limitation: the value written here is computed from the .vmoptions files and from the arguments of the
   * running JVM only. What the properties files declare is invisible to that computation, so it is not carried over
   * - turning the wrapper on writes an option that overrides those files for this IDE.
   *
   * @return true when the user's .vmoptions file was changed, so the IDE has to be restarted to apply it
   */
  private fun updateAtkWrapperVmOption(shouldEnableAtkWrapper: Boolean): Boolean {
    if (!SystemInfoRt.isLinux || !VMOptions.canWriteOptions()) {
      return false
    }

    val configuredOptionValue = VMOptions.readOption(ASSISTIVE_TECHNOLOGIES_VM_OPTION_PREFIX, false)
    val runtimeOptionValue = VMOptions.readOption(ASSISTIVE_TECHNOLOGIES_VM_OPTION_PREFIX, true)
    // optionValue is the option in the .vmoptions files, or the arguments of the running JVM when the files say
    // nothing about it. See the known limitation above for what is not part of it.
    val newOptionValue = computeAssistiveTechnologiesOptionValue(
      optionValue = configuredOptionValue ?: runtimeOptionValue,
      shouldEnableAtkWrapper = shouldEnableAtkWrapper,
    )

    return newOptionValue != configuredOptionValue && runCatching {
      // Only the user's .vmoptions file is rewritten here, and a null value deletes the option from it.
      // The properties files are left as they are; see the KDoc above for why the option is deleted
      // rather than set to an empty value, and what the JVM falls back to afterward.
      VMOptions.setProperty(ASSISTIVE_TECHNOLOGIES_PROPERTY, newOptionValue)
    }.onFailure {
      LOG.warn("Failed to update custom VM options for Java ATK Wrapper support. " +
               "Could not persist '$ASSISTIVE_TECHNOLOGIES_PROPERTY'.", it)
    }.isSuccess
  }

  fun isLinuxScreenReaderEnabled(): Boolean {
    return isGnomeScreenReaderSettingEnabled() || isOrcaProcessRunning()
  }

  /**
   * Adds [ATK_WRAPPER] to a comma-separated `javax.accessibility.assistive_technologies` value, or removes it.
   *
   * @param optionValue the value the option currently has, or null when it is not set
   * @param shouldEnableAtkWrapper whether the Java ATK Wrapper has to be present in the resulting value
   *
   * @return the comma-separated value to write into the option, or null when no assistive technologies are left,
   *   in which case the caller removes the option from the .vmoptions file
   */
  private fun computeAssistiveTechnologiesOptionValue(
    optionValue: String?,
    shouldEnableAtkWrapper: Boolean,
  ): String? {
    val assistiveTechnologies = parseAssistiveTechnologiesOptionValue(optionValue)
    if (shouldEnableAtkWrapper) {
      assistiveTechnologies.add(ATK_WRAPPER)
    }
    else {
      assistiveTechnologies.remove(ATK_WRAPPER)
    }

    return assistiveTechnologies
      .takeIf { it.isNotEmpty() }
      ?.joinToString(",")
  }

  private fun isLinuxScreenMagnifierEnabled(): Boolean {
    return isGnomeZoomEnabled()
  }

  /**
   * `javax.accessibility.assistive_technologies` is a comma-delimited list
   * (https://docs.oracle.com/en/java/javase/21/access/accessibility-properties.html)
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

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.icons.AllIcons
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.IconMapLoader
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.util.PlatformUtils

private val LOG: Logger
  get() = logger<ExperimentalUI>()

/**
 * @author Konstantin Bulenkov
 */
private class ExperimentalUIImpl : ExperimentalUI() {
  private var shouldApplyOnClose: Boolean? = null
  private var shouldUnsetNewUiSwitchKey: Boolean = true

  override fun getIconMappings(): Map<ClassLoader, Map<String, String>> = service<IconMapLoader>().loadIconMapping()

  /**
   * For RD session, we take the newUI preference from the join link of IDE backend,
   * and we don't read from or write to a local thin client registry.
   *
   * For CWM session, we take the newUI preference from a local thin client registry,
   * and when a user changes the value, we write it to the local registry.
   *
   * Both for RD and CWM sessions an actual change of newUI preference is done by
   * [ExperimentalUIJetBrainsClientDelegate].
   *
   * For local IDE, we show a restart dialog on user action.
   * On app closing, we save new value stored in the [shouldApplyOnClose]
   */
  override fun setNewUIInternal(newUI: Boolean, suggestRestart: Boolean) {
    if (newUI == NewUiValue.isEnabled()) {
      LOG.warn("Setting the same value $newUI")
      return
    }

    if (PlatformUtils.isJetBrainsClient()) {
      changeUiWithDelegate(newUI)
    }
    else {
      onValueChanged(newUI)
      if (suggestRestart) {
        shouldApplyOnClose = newUI
        showRestartDialog()
      }
      else {
        saveNewValue(newUI)
      }
    }
  }

  override fun onExpUIEnabled(suggestRestart: Boolean) {
    onValueChanged(isEnabled = true)
  }

  override fun onExpUIDisabled(suggestRestart: Boolean) {
    onValueChanged(isEnabled = false)
  }

  fun appStarted() {
    if (isNewUI()) {
      val version = ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot()
      PropertiesComponent.getInstance().setValue(NEW_UI_USED_VERSION, version)
    }
  }

  fun appClosing() {
    if (shouldUnsetNewUiSwitchKey) {
      PropertiesComponent.getInstance().unsetValue(NEW_UI_SWITCH)
    }
    val newValue = shouldApplyOnClose
    if (newValue != null && newValue != NewUiValue.isEnabled()) {
      saveNewValue(newValue)
    }
  }

  private fun onValueChanged(isEnabled: Boolean) {
    if (isEnabled) {
      setNewUiUsed()
    }

    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    if (isEnabled) {
      NewUIInfoService.getInstance().updateEnableNewUIDate()
      // Do not force enabling tool window stripes in DFM
      if (!DistractionFreeModeController.shouldMinimizeCustomHeader()) {
        UISettings.getInstance().hideToolStripes = false
      }
    }
    else {
      NewUIInfoService.getInstance().updateDisableNewUIDate()
    }

    // On the client, onValueChanged will not be called again as there's no real registry value change.
    // Set the override before calling resetLafSettingsToDefault to ensure the correct LaF is chosen.
    if (PlatformUtils.isJetBrainsClient()) {
      NewUiValue.overrideNewUiForOneRemDevSession(isEnabled)
    }
    resetLafSettingsToDefault()
  }

  private fun saveNewValue(enabled: Boolean) {
    try {
      LOG.info("Saving newUi=$enabled to registry")
      EarlyAccessRegistryManager.setBoolean(KEY, enabled)
      EarlyAccessRegistryManager.syncAndFlush()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  override fun saveCurrentValueAndReapplyDefaultLaf() {
    saveNewValue(NewUiValue.isEnabled())
    resetLafSettingsToDefault()
  }

  private fun setNewUiUsed() {
    val propertyComponent = PropertiesComponent.getInstance()
    if (isNewUiUsedOnce()) {
      propertyComponent.unsetValue(NEW_UI_FIRST_SWITCH)
    }
    else {
      propertyComponent.setValue(NEW_UI_FIRST_SWITCH, true)
    }
    propertyComponent.setValue(NEW_UI_SWITCH, true)
    shouldUnsetNewUiSwitchKey = false
  }

  private fun changeUiWithDelegate(isEnabled: Boolean) {
    val shouldRestart = MessageDialogBuilder.yesNo(
      title = IdeBundle.message("dialog.newui.title.user.interface"),
      message = IdeBundle.message("dialog.newui.message.need.restart.client.and.backend.to.apply.settings"),
      icon = AllIcons.General.QuestionDialog,
    ).yesText(IdeBundle.message("dialog.newui.message.new.ui.restart"))
      .noText(IdeBundle.message("dialog.newui.message.new.ui.revert"))
      .guessWindowAndAsk()
    if (shouldRestart) {
      val delegate = ExperimentalUIJetBrainsClientDelegate.getInstance()
      delegate.changeUi(isEnabled, updateLocally = {
        onValueChanged(isEnabled)
        saveNewValue(isEnabled)
      })
    }
  }

  private fun showRestartDialog() {
    val action = if (ApplicationManager.getApplication().isRestartCapable) {
      IdeBundle.message("ide.restart.action")
    }
    else {
      IdeBundle.message("ide.shutdown.action")
    }
    val result = Messages.showYesNoDialog(
      /* message = */ IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                        ApplicationNamesInfo.getInstance().fullProductName),
      /* title = */ IdeBundle.message("dialog.title.restart.required"),
      /* yesText = */ action,
      /* noText = */ IdeBundle.message("ide.notnow.action"),
      /* icon = */ Messages.getQuestionIcon()
    )

    if (result == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true)
    }
  }
}

private fun resetLafSettingsToDefault() {
  val lafManager = LafManager.getInstance()
  val defaultLightLaf = lafManager.defaultLightLaf ?: return
  val defaultDarkLaf = lafManager.defaultDarkLaf ?: return
  val laf = if (JBColor.isBright()) defaultLightLaf else defaultDarkLaf
  lafManager.currentLookAndFeel = laf
  if (lafManager.autodetect) {
    lafManager.setPreferredLightLaf(defaultLightLaf)
    lafManager.setPreferredDarkLaf(defaultDarkLaf)
  }
}

/**
 * We can't implement AppLifecycleListener with ExperimentalUiImpl
 * because it would create another instance of ExperimentalUiImpl
 */
private class ExperimentalUiAppLifecycleListener : AppLifecycleListener {
  override fun appStarted() {
    (ExperimentalUI.getInstance() as? ExperimentalUIImpl)
      ?.appStarted()
  }

  override fun appClosing() {
    (ExperimentalUI.getInstance() as? ExperimentalUIImpl)
      ?.appClosing()
  }
}

interface ExperimentalUIJetBrainsClientDelegate {
  companion object {
    fun getInstance() = service<ExperimentalUIJetBrainsClientDelegate>()
  }

  fun changeUi(isEnabled: Boolean, updateLocally: (Boolean) -> Unit)
}
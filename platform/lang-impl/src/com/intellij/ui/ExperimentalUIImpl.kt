// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.icons.AllIcons
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.IconMapLoader
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import com.intellij.util.application

/**
 * @author Konstantin Bulenkov
 */
private class ExperimentalUIImpl : ExperimentalUI() {
  companion object {
    private val logger = logger<ExperimentalUI>()
  }

  private var shouldApplyOnClose: Boolean? = null

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
    if (newUI == NewUi.isEnabled()) {
      logger.warn("Setting the same value $newUI")
      return
    }

    if (PlatformUtils.isJetBrainsClient()) {
      changeUiWithDelegate(newUI)
    }
    else if (suggestRestart && !PlatformUtils.isJetBrainsClient()) {
      onValueChanged(newUI)
      shouldApplyOnClose = newUI
      showRestartDialog()
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
      PropertiesComponent.getInstance()
        .setValue(NEW_UI_USED_PROPERTY, true)
    }
  }

  fun appClosing() {
    val newValue = shouldApplyOnClose
    if (newValue != null && newValue != NewUi.isEnabled()) {
      saveNewValue(newValue)
    }
  }

  private fun onValueChanged(isEnabled: Boolean) {
    if (isEnabled) setNewUiUsed()

    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    if (isEnabled) {
      NewUIInfoService.getInstance().updateEnableNewUIDate()
      UISettings.getInstance().hideToolStripes = false
    }
    else {
      NewUIInfoService.getInstance().updateDisableNewUIDate()
    }

    resetLafSettingsToDefault()
  }

  private fun saveNewValue(enabled: Boolean) {
    try {
      logger.info("Saving newUi=$enabled to registry")
      Registry.get(KEY).setValue(enabled)
    }
    catch (e: Throwable) {
      logger.error(e)
    }
  }

  private fun setNewUiUsed() {
    val propertyComponent = PropertiesComponent.getInstance()
    if (propertyComponent.getBoolean(NEW_UI_USED_PROPERTY)) {
      propertyComponent.unsetValue(NEW_UI_FIRST_SWITCH)
    }
    else {
      propertyComponent.setValue(NEW_UI_FIRST_SWITCH, true)
    }
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
      val delegate = application.service<ExperimentalUIJetBrainsClientDelegate>()
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

  private fun resetLafSettingsToDefault() {
    val lafManager = LafManager.getInstance()
    val defaultLightLaf = lafManager.defaultLightLaf
    val defaultDarkLaf = lafManager.defaultDarkLaf
    if (defaultLightLaf == null || defaultDarkLaf == null) {
      return
    }

    val laf = if (JBColor.isBright()) defaultLightLaf else defaultDarkLaf
    lafManager.currentLookAndFeel = laf
    if (lafManager.autodetect) {
      lafManager.setPreferredLightLaf(defaultLightLaf)
      lafManager.setPreferredDarkLaf(defaultDarkLaf)
    }
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
  fun changeUi(isEnabled: Boolean, updateLocally: (Boolean) -> Unit)
}
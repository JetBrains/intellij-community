// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.feedback.new_ui.state.NewUIInfoService
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
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.PlatformUtils

/**
 * @author Konstantin Bulenkov
 */
private class ExperimentalUIImpl : ExperimentalUI() {
  @JvmField
  var newValue: Boolean = isNewUI()

  override fun getIconMappings(): Map<ClassLoader, Map<String, String>> = service<IconMapLoader>().loadIconMapping()

  override fun setNewUIInternal(newUI: Boolean, suggestRestart: Boolean) {
    if (newUI) {
      val propertyComponent = PropertiesComponent.getInstance()
      if (propertyComponent.getBoolean(NEW_UI_USED_PROPERTY)) {
        propertyComponent.unsetValue(NEW_UI_FIRST_SWITCH)
      }
      else {
        propertyComponent.setValue(NEW_UI_FIRST_SWITCH, true)
      }
    }

    newValue = newUI

    if (newValue != isNewUI() && suggestRestart) {
      val action = if (ApplicationManager.getApplication().isRestartCapable) {
        IdeBundle.message("ide.restart.action")
      }
      else {
        IdeBundle.message("ide.shutdown.action")
      }
      if (PlatformUtils.isJetBrainsClient()) {
        Registry.get("ide.experimental.ui").setValue(newValue)
      }
      else {
        val result = Messages.showYesNoDialog(IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                                                ApplicationNamesInfo.getInstance().fullProductName),
                                              IdeBundle.message("dialog.title.restart.required"),
                                              action,
                                              IdeBundle.message("ide.notnow.action"),
                                              Messages.getQuestionIcon())


        if (result == Messages.YES) {
          ApplicationManagerEx.getApplicationEx().restart(true)
        }
      }
    }
  }

  override fun onExpUIEnabled(suggestRestart: Boolean) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    newValue = true
    NewUIInfoService.getInstance().updateEnableNewUIDate()

    val registryManager = RegistryManager.getInstance()
    setRegistryKeyIfNecessary(key = "ide.experimental.ui", value = true, registryManager = registryManager)
    setRegistryKeyIfNecessary(key = "debugger.new.tool.window.layout", value = true, registryManager = registryManager)
    UISettings.getInstance().hideToolStripes = false
    resetLafSettingsToDefault()
  }

  override fun onExpUIDisabled(suggestRestart: Boolean) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    newValue = false
    NewUIInfoService.getInstance().updateDisableNewUIDate()

    val registryManager = RegistryManager.getInstance()
    setRegistryKeyIfNecessary(key = "ide.experimental.ui", value = false, registryManager = registryManager)
    setRegistryKeyIfNecessary(key = "debugger.new.tool.window.layout", value = false, registryManager = registryManager)
    resetLafSettingsToDefault()
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

private fun setRegistryKeyIfNecessary(key: String, value: Boolean, registryManager: RegistryManager) {
  val registryValue = registryManager.get(key)
  if (registryValue.isBoolean != value) {
    registryValue.setValue(value)
  }
}

private class ExperimentalUiAppLifecycleListener : AppLifecycleListener {
  override fun appStarted() {
    if (ExperimentalUI.isNewUI()) {
      PropertiesComponent.getInstance().setValue(ExperimentalUI.NEW_UI_USED_PROPERTY, true)
    }
  }

  override fun appClosing() {
    val experimentalUi = (ExperimentalUI.getInstance() as? ExperimentalUIImpl) ?: return
    val newValue = experimentalUi.newValue
    if (newValue != ExperimentalUI.isNewUI()) {
      // if RegistryManager not yet created on appClosing, it means that something fatal is occurred, do not try to use it
      val registryManager = ApplicationManager.getApplication().serviceIfCreated<RegistryManager>() ?: return
      registryManager.get("ide.experimental.ui").setValue(newValue)
      if (newValue) {
        experimentalUi.onExpUIEnabled(suggestRestart = false)
      }
      else {
        experimentalUi.onExpUIDisabled(suggestRestart = false)
      }
    }
  }
}
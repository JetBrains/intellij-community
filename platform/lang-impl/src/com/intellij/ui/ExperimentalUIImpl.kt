// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.IconMapLoader
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils

/**
 * @author Konstantin Bulenkov
 */
class ExperimentalUIImpl : ExperimentalUI(), AppLifecycleListener {
  var newValue: Boolean = isNewUI()

  init {
    val app = ApplicationManager.getApplication()
    app.messageBus.connect().subscribe(AppLifecycleListener.TOPIC, this)
    if (ConfigImportHelper.isNewUser() && isNewUIEnabledByDefault() && !isNewUI()) {
      app.invokeLater {
        newValue = true
        onExpUIEnabled(false)
      }
    }
  }

  private fun isNewUIEnabledByDefault() = PlatformUtils.isAqua() ||
                                          PlatformUtils.isPyCharmCommunity() ||
                                          PlatformUtils.isWebStorm()

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
      val action = if (ApplicationManager.getApplication().isRestartCapable) IdeBundle.message("ide.restart.action")
      else IdeBundle.message("ide.shutdown.action")
      val result = Messages.showYesNoDialog(IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                                              ApplicationNamesInfo.getInstance().fullProductName),
                                            IdeBundle.message("dialog.title.restart.required"),
                                            action,
                                            IdeBundle.message("ide.notnow.action"),
                                            Messages.getQuestionIcon())
      if (result == Messages.YES) {
        ApplicationManagerEx.getApplicationEx().restart(true);
      }
    }
  }

  override fun appStarted() {
    if (isNewUI()) {
      val propertyComponent = PropertiesComponent.getInstance()
      propertyComponent.setValue(NEW_UI_USED_PROPERTY, true)
    }
  }

  override fun appClosing() {
    if (newValue != isNewUI()) {
      Registry.get("ide.experimental.ui").setValue(newValue)
      if (newValue) {
        onExpUIEnabled(false)
      } else {
        onExpUIDisabled(false)
      }
    }
  }

  override fun onExpUIEnabled(suggestRestart: Boolean) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    NewUIInfoService.getInstance().updateEnableNewUIDate()

    setRegistryKeyIfNecessary("ide.experimental.ui", true)
    setRegistryKeyIfNecessary("debugger.new.tool.window.layout", true)
    UISettings.getInstance().hideToolStripes = false
    val name = if (JBColor.isBright()) "Light" else "Dark"
    val lafManager = LafManager.getInstance()
    val laf = lafManager.installedLookAndFeels.firstOrNull { it.name == name }
    if (laf != null) {
      lafManager.currentLookAndFeel = laf
      if (lafManager.autodetect) {
        if (JBColor.isBright()) {
          lafManager.setPreferredLightLaf(laf)
        }
        else {
          lafManager.setPreferredDarkLaf(laf)
        }
      }
    }
  }

  override fun onExpUIDisabled(suggestRestart: Boolean) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) return

    NewUIInfoService.getInstance().updateDisableNewUIDate()

    setRegistryKeyIfNecessary("ide.experimental.ui", false)
    setRegistryKeyIfNecessary("debugger.new.tool.window.layout", false)
    val lafManager = LafManager.getInstance() as LafManagerImpl
    val currentLafName = lafManager.currentLookAndFeel?.name
    if (currentLafName == "Dark" || currentLafName == "Light") {
      val laf = if (JBColor.isBright()) lafManager.defaultLightLaf!! else lafManager.getDefaultDarkLaf()
      lafManager.setCurrentLookAndFeel(laf)
      if (lafManager.autodetect) {
        if (JBColor.isBright()) {
          lafManager.setPreferredLightLaf(laf)
        }
        else {
          lafManager.setPreferredDarkLaf(laf)
        }
      }
    }
  }

  companion object {
    private fun setRegistryKeyIfNecessary(key: String, value: Boolean) {
      if (Registry.`is`(key) != value) {
        Registry.get(key).setValue(value)
      }
    }
  }
}
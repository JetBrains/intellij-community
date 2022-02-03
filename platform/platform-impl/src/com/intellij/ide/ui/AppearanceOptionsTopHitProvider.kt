// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.util.text.Strings
import com.intellij.ui.ExperimentalUI
import java.util.function.Supplier

const val APPEARANCE_ID = "appearance"

internal val uiOptionGroupName
  get() = message("appearance.ui.option.group")
internal val windowOptionGroupName
  get() = message("appearance.window.option.group")
internal val viewOptionGroupName
  get() = message("appearance.view.option.group")

private val settings
  get() = UISettings.instance
private val notificationSettings
  get() = NotificationsConfigurationImpl.getInstanceImpl()

private val cdShowMainToolbar
  get() = CheckboxDescriptor(message("show.main.toolbar"), settings::showMainToolbar, groupName = viewOptionGroupName)
private val cdShowStatusBar get() = CheckboxDescriptor(message("show.status.bar"), settings::showStatusBar, groupName = viewOptionGroupName)
private val cdShowNavigationBar
  get() = CheckboxDescriptor(message("show.navigation.bar"), settings::showNavigationBar, groupName = viewOptionGroupName)
private val cdShowMembersInNavigationBar
  get() = CheckboxDescriptor(message("show.members.in.navigation.bar"), settings::showMembersInNavigationBar,
                             groupName = viewOptionGroupName)
private val cdUseSmallTabLabels
  get() = CheckboxDescriptor(message("small.labels.in.editor.tabs"), settings::useSmallLabelsOnTabs, groupName = windowOptionGroupName)
private val cdShowEditorPreview
  get() = CheckboxDescriptor(OptionsTopHitProvider.messageIde("checkbox.show.editor.preview.popup"), settings::showEditorToolTip,
                             groupName = windowOptionGroupName)
private val cdShowBalloons
  get() = CheckboxDescriptor(message("display.balloon.notifications"), notificationSettings::SHOW_BALLOONS, groupName = uiOptionGroupName)

internal class AppearanceOptionsTopHitProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId() = APPEARANCE_ID

  override fun getOptions(): List<OptionDescription> {
    var list = sequenceOf(
      cdShowMainToolbar,
      cdShowStatusBar,
      cdShowNavigationBar,
      cdShowMembersInNavigationBar)
    if (!ExperimentalUI.isNewUI()) {
      list += cdUseSmallTabLabels
    }
    list += cdShowEditorPreview
    list += cdShowBalloons
    val allOptions = getAppearanceOptionDescriptors() + list.map(CheckboxDescriptor::asUiOptionDescriptor)
    return allOptions.toList()
  }

  companion object {
    @JvmStatic
    fun option(@Label option: String, propertyName: String, configurableId: String): BooleanOptionDescription {
      return object : PublicMethodBasedOptionDescription(option, configurableId,
                                                         "get" + Strings.capitalize(propertyName),
                                                         "set" + Strings.capitalize(propertyName), Supplier { UISettings.instance.state }) {
        override fun fireUpdated() = UISettings.instance.fireUISettingsChanged()
      }
    }

    @JvmStatic
    fun appearance(@Label option: String, propertyName: String) = option(option, propertyName, "preferences.lookFeel")
  }
}
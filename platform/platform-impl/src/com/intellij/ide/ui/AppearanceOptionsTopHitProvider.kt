// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.util.text.StringUtil

// @formatter:off
internal val uiOptionGroupName get() = message("appearance.ui.option.group")
internal val windowOptionGroupName get() = message("appearance.window.option.group")
internal val viewOptionGroupName get() = message("appearance.view.option.group")

private val settings get() = UISettings.instance
private val notificationSettings get() = NotificationsConfigurationImpl.getInstanceImpl()

private val cdShowMainToolbar                  get() = CheckboxDescriptor(message("show.main.toolbar"), settings::showMainToolbar, groupName = viewOptionGroupName)
private val cdShowStatusBar                    get() = CheckboxDescriptor(message("show.status.bar"), settings::showStatusBar, groupName = viewOptionGroupName)
private val cdShowNavigationBar                get() = CheckboxDescriptor(message("show.navigation.bar"), settings::showNavigationBar, groupName = viewOptionGroupName)
private val cdShowMembersInNavigationBar       get() = CheckboxDescriptor(message("show.members.in.navigation.bar"), settings::showMembersInNavigationBar, groupName = viewOptionGroupName)
private val cdUseSmallTabLabels                get() = CheckboxDescriptor(message("small.labels.in.editor.tabs"), settings::useSmallLabelsOnTabs, groupName = windowOptionGroupName)
private val cdNavigateToPreview                get() = CheckboxDescriptor(OptionsTopHitProvider.messageIde("checkbox.use.preview.window"), settings::navigateToPreview, groupName = windowOptionGroupName)
private val cdShowEditorPreview                get() = CheckboxDescriptor(OptionsTopHitProvider.messageIde("checkbox.show.editor.preview.popup"), settings::showEditorToolTip, groupName = windowOptionGroupName)
private val cdShowBalloons                     get() = CheckboxDescriptor(message("display.balloon.notifications"), notificationSettings::SHOW_BALLOONS, groupName = uiOptionGroupName)
// @formatter:on

private val optionDescriptors
  get() = listOf(
    cdShowMainToolbar,
    cdShowStatusBar,
    cdShowNavigationBar,
    cdShowMembersInNavigationBar,
    cdUseSmallTabLabels,
    cdNavigateToPreview,
    cdShowEditorPreview,
    cdShowBalloons
  ).map(CheckboxDescriptor::asUiOptionDescriptor)

class AppearanceOptionsTopHitProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId(): String = ID

  override fun getOptions(): Collection<OptionDescription> {
    return appearanceOptionDescriptors +
           optionDescriptors
  }

  companion object {
    const val ID = "appearance"

    @JvmStatic
    fun option(option: @Label String, propertyName: String, configurableId: String): BooleanOptionDescription =
      object : PublicMethodBasedOptionDescription(option, configurableId,
                                                  "get" + StringUtil.capitalize(propertyName),
                                                  "set" + StringUtil.capitalize(propertyName)) {
        override fun getInstance(): Any = UISettings.instance.state
        override fun fireUpdated() = UISettings.instance.fireUISettingsChanged()
      }

    @JvmStatic
    fun appearance(option: @Label String, propertyName: String): BooleanOptionDescription = option(option, propertyName, "preferences.lookFeel")
  }
}
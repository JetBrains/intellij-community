// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.DialogBackgroundImageProviderBase
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenFeatureApi
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenFeatureUI
import com.intellij.platform.project.projectId
import com.intellij.util.IconUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Image
import java.net.URL
import javax.swing.Icon

internal class IdeaWelcomeRightTabContentProvider(override val coroutineScope: CoroutineScope) : WelcomeRightTabContentProvider {
  private val bgImageLoader = object : DialogBackgroundImageProviderBase() {
    override fun getImageUrl(isDark: Boolean, isIslands: Boolean): URL? {
      return javaClass.getResource(if (isDark) "/images/Idea-Dark.svg" else "/images/Idea-Light.svg")
    }
  }

  override val backgroundImageVectorLight: Image by lazy { bgImageLoader.getImage(false, false)!! }
  override val backgroundImageVectorDark: Image by lazy { bgImageLoader.getImage(true, false)!! }

  override val title = IdeBundle.messagePointer("idea.non.modal.welcome.screen.right.tab.header")
  override val secondaryTitle = IdeBundle.messagePointer("idea.non.modal.welcome.screen.right.tab.secondary.header")

  override val isDisableOptionVisible = false

  override val fileTypeIcon = AllIcons.Ultimate.IdeaUltimatePromo

  override fun getFeatureButtonModels(project: Project): List<WelcomeRightTabContentProvider.FeatureButtonModel> {
    return listOfNotNull(
      pluginProvidedFeatureButtonModel(IdeaFeatureKeys.AIR_SESSIONS),
      WelcomeRightTabContentProvider.FeatureButtonModel(
        text = IdeBundle.message("configurable.TerminalOptionsConfigurable.display.name"),
        icon = scale(AllIcons.Debugger.Console, true),
        onClick = { _, _ ->
          featureButtonOnClick(project, IdeaFeatureKeys.TERMINAL)
        }
      ),
      WelcomeRightTabContentProvider.FeatureButtonModel(
        text = IdeBundle.message("welcome.screen.plugins.title"),
        icon = scale(AllIcons.Nodes.Plugin, true),
        onClick = { _, _ ->
          featureButtonOnClick(project, IdeaFeatureKeys.PLUGINS)
        }
      )
    )
  }

  /**
   * Builds a button for a feature a plugin owns, or returns `null` when the plugin ships no button.
   *
   * The plugin supplies the icon and the label, so its own message bundle keeps the wording. The backend half gates
   * the button too: the welcome right tab drops a [WelcomeRightTabContentProvider.FeatureButtonModelWithBackend]
   * whose `welcomeScreenFeatureBackend` no loaded plugin registers.
   */
  private fun pluginProvidedFeatureButtonModel(featureKey: String): WelcomeRightTabContentProvider.FeatureButtonModel? {
    val feature = WelcomeScreenFeatureUI.getForFeatureKey(featureKey) ?: return null
    val text = feature.text ?: return null
    return WelcomeRightTabContentProvider.FeatureButtonModelWithBackend(
      featureKey = feature.featureKey,
      text = text,
      icon = scale(feature.icon, true),
    )
  }

  private fun scale(icon: Icon, color: Boolean) = IconUtil.scale(icon, null, 1.5f) // TODO: colorize to blue

  private fun featureButtonOnClick(project: Project, featureKey: String) {
    coroutineScope.launch {
      val api = WelcomeScreenFeatureApi.getInstance()
      api.onClick(project.projectId(), featureKey)
    }
  }
}
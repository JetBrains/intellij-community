// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.DialogBackgroundImageProviderBase
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenFeatureApi
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenFeatureUI
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Image
import java.net.URL

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

  override val fileTypeIcon = AllIcons.Ultimate.IdeaUltimatePromo

  /**
   * The Air plugin contributes the agent prompt input as a section above the feature grid, under its own feature key.
   * That input is the tab's call to action on its own, so the grid stays empty while the plugin is there, and these
   * buttons stand in for the input while it is not.
   */
  override fun getFeatureButtonModels(project: Project): List<WelcomeRightTabContentProvider.FeatureButtonModel> {
    if (hasAgentPromptInput()) {
      return emptyList()
    }

    return listOf(
      WelcomeRightTabContentProvider.FeatureButtonModel(
        text = IdeBundle.message("idea.non.modal.welcome.screen.new.file"),
        icon = AllIcons.FileTypes.Text,
        onClick = { _, _ ->
          featureButtonOnClick(project, IdeaFeatureKeys.NEW_FILE)
        }
      ),
      WelcomeRightTabContentProvider.FeatureButtonModel(
        text = IdeBundle.message("idea.non.modal.welcome.screen.open.terminal"),
        icon = AllIcons.Debugger.Console,
        onClick = { _, _ ->
          featureButtonOnClick(project, IdeaFeatureKeys.TERMINAL)
        }
      )
    )
  }

  /**
   * Whether the Air plugin places its prompt input on the tab.
   *
   * The plugin has to be there, and its prompt has to be on. [AIR_WELCOME_SCREEN_PROMPT_REGISTRY_KEY] is the only
   * thing that turns the prompt off, so reading the key answers for the section itself. The key is on by default,
   * which is the default this read states as well.
   */
  private fun hasAgentPromptInput(): Boolean {
    if (WelcomeScreenFeatureUI.getForFeatureKey(IdeaFeatureKeys.AIR_SESSIONS) == null) {
      return false
    }
    return Registry.`is`(AIR_WELCOME_SCREEN_PROMPT_REGISTRY_KEY, true)
  }

  /**
   * Builds a button for a feature a plugin owns, or returns `null` when the plugin ships no button.
   *
   * The plugin supplies the icon and the label, so its own message bundle keeps the wording. The handler half gates
   * the button too: the welcome right tab drops a [WelcomeRightTabContentProvider.FeatureButtonModelWithBackend]
   * whose `welcomeScreenFeatureFrontend` or `welcomeScreenFeatureBackend` no loaded plugin registers.
   */
  private fun pluginProvidedFeatureButtonModel(featureKey: String): WelcomeRightTabContentProvider.FeatureButtonModel? {
    val feature = WelcomeScreenFeatureUI.getForFeatureKey(featureKey) ?: return null
    val text = feature.text ?: return null
    return WelcomeRightTabContentProvider.FeatureButtonModelWithBackend(
      featureKey = feature.featureKey,
      text = text,
      icon = feature.icon
    )
  }

  private fun featureButtonOnClick(project: Project, featureKey: String) {
    coroutineScope.launch {
      val api = WelcomeScreenFeatureApi.getInstance()
      api.onClick(project.projectId(), featureKey)
    }
  }
}

/**
 * The registry key of the Air prompt input on the welcome right tab.
 *
 * Mirrors `WELCOME_SCREEN_PROMPT_REGISTRY_KEY`, which the Air plugin keeps internal. The tab itself never states
 * which of its sections were built, so the button grid reads the key that decides the only section there is.
 */
private const val AIR_WELCOME_SCREEN_PROMPT_REGISTRY_KEY = "air.welcome.screen.inline.prompt"

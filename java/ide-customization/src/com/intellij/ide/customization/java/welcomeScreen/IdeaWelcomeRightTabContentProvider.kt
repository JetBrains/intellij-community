// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.project.Project
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

  override fun getFeatureButtonModels(project: Project): List<WelcomeRightTabContentProvider.FeatureButtonModel> {
    return listOfNotNull(
      WelcomeRightTabContentProvider.FeatureButtonModel(
        text = ActionsBundle.message ("action.NewJavaFile.text"),
        icon = AllIcons.FileTypes.Java,
        onClick = { _, _ ->
          featureButtonOnClick(project, IdeaFeatureKeys.NEW_JAVA_FILE)
        }
      ),
      WelcomeRightTabContentProvider.FeatureButtonModel(
        text = ActionsBundle.message("action.NewKotlinFile.text"),
        icon = AllIcons.Language.Kotlin,
        onClick = { _, _ ->
          featureButtonOnClick(project, IdeaFeatureKeys.NEW_KOTLIN_FILE)
        }
      ),
      WelcomeRightTabContentProvider.FeatureButtonModel(
        text = ActionsBundle.message("action.AttachDebuger.text"),
        icon = AllIcons.Toolwindows.ToolWindowDebugger,
        onClick = { _, _ ->
          featureButtonOnClick(project, IdeaFeatureKeys.ATTACH_TO_PROCESS)
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
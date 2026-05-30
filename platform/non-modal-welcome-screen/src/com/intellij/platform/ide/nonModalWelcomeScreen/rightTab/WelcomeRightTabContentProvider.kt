// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.DefaultFileDragAndDropHandler
import com.intellij.platform.ide.nonModalWelcomeScreen.FileDragAndDropHandler
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.ui.icon.IconKey
import java.util.function.Supplier
import javax.swing.Icon

@ApiStatus.Internal
interface WelcomeRightTabContentProvider {
  val coroutineScope: CoroutineScope

  // Use Valkyrie IDEA plugin to generate the ImageVector
  val backgroundImageVectorLight: ImageVector
  val backgroundImageVectorDark: ImageVector

  val fileTypeIcon: Icon
  val title: Supplier<@Nls String>
  val secondaryTitle: Supplier<@Nls String>

  val isDisableOptionVisible: Boolean
  val isStartupSwitchPanelOptionVisible: Boolean
    get() = false

  val buttonsPerRow: Int
    get() = 3

  fun shouldBeFocused(project: Project): Boolean {
    return project.service<WelcomeScreenPreventWelcomeTabFocusService>().isAllowedFocusOnWelcomeTab()
  }

  @Composable
  fun getFeatureButtonModels(project: Project): List<FeatureButtonModel>

  @Composable
  fun getAdditionalInfoButtonModels(project: Project): List<InfoButtonModel> = emptyList()

  /**
   * Button model for additional buttons displayed at the bottom of the welcome screen.
   * These will be displayed after the default theme and keymap buttons.
   */
  class InfoButtonModel(
    val text: String,
    val icon: IconKey,
    val onClick: (Project, CoroutineScope) -> Unit,
  )

  @Composable
  fun getAdditionalLinks(project: Project): List<LinkModel> = emptyList()

  fun getFileDragAndDropHandler(): FileDragAndDropHandler = DefaultFileDragAndDropHandler

  /**
   * Optional link rendered below the feature grid (above the banner) on the welcome right tab.
   * Rendered as an external link with the standard external-arrow icon.
   */
  class LinkModel(
    val text: @Nls String,
    val onClick: (Project) -> Unit,
    val tint: Color = Color.Unspecified,
    val tintHovered: Color = Color.Unspecified,
  )

  /**
   * Base feature button model. Use for frontend-only features.
   * For backend-based features, use [FeatureButtonModelWithBackend] and
   * register a `WelcomeScreenFeatureBackend` implementation.
   */
  open class FeatureButtonModel(
    val text: String,
    val icon: IconKey,
    val tint: Color = Color.Unspecified,
    val onClick: (Project, CoroutineScope) -> Unit,
  )

  /**
   * Feature button backed by a `WelcomeScreenFeatureBackend` implementation.
   */
  class FeatureButtonModelWithBackend(
    val featureKey: String,
    val isAlwaysAvailable: Boolean = false,
    text: String,
    icon: IconKey,
    tint: Color = Color.Unspecified,
    val beforeOnClick: suspend (Project) -> Unit = {}
  ) : FeatureButtonModel(text, icon, tint, { project, coroutineScope ->
    coroutineScope.launch {
      beforeOnClick(project)
      WelcomeScreenFeatureApi.getInstance().onClick(project.projectId(), featureKey)
    }
  })

  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeRightTabContentProvider> = ExtensionPointName("com.intellij.platform.ide.welcomeScreenContentProvider")

    fun getSingleExtension(): WelcomeRightTabContentProvider? {
      val providers = EP_NAME.extensionList
      if (providers.isEmpty()) return null
      if (providers.size > 1) {
        thisLogger().warn("Multiple WelcomeRightTabContentProvider extensions")
        return null
      }
      return providers.first()
    }

    fun getPluginProvidedFeature(featureKey: String): WelcomeScreenFeatureUI? {
      val feature = WelcomeScreenFeatureUI.getForFeatureKey(featureKey)
      if (feature == null) {
        thisLogger().warn("Feature for the feature key $featureKey not found")
      }
      return feature
    }
  }
}

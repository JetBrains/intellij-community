// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.ui.icon.IconKey
import java.util.function.Supplier

@ApiStatus.Internal
interface WelcomeRightTabContentProvider {
  // Use Valkyrie IDEA plugin to generate the ImageVector
  val backgroundImageVectorLight: ImageVector
  val backgroundImageVectorDark: ImageVector

  val title: Supplier<@Nls String>
  val secondaryTitle: Supplier<@Nls String>

  fun shouldBeFocused(project: Project): Boolean = true

  @Composable
  fun getFeatureButtonModels(project: Project): List<FeatureButtonModel>

  data class FeatureButtonModel(
    val text: String,
    val icon: IconKey,
    val tint: Color = Color.Unspecified,
    val onClick: () -> Unit,
  )

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

    fun getPluginProvidedFeature(featureKey: String): WelcomeScreenFeature? {
      val feature = WelcomeScreenFeature.getForFeatureKey(featureKey)
      if (feature == null) {
        thisLogger().warn("Feature for the feature key $featureKey not found")
      }
      return feature
    }
  }
}

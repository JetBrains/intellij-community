package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface WelcomeScreenRightTabBannerProvider {
  companion object {
    private val EP_NAME = ExtensionPointName<WelcomeScreenRightTabBannerProvider>("com.intellij.platform.ide.welcomeScreenRightTabBannerProvider")

    @Composable
    fun SingleBanner(project: Project, modifier: Modifier) {
      val provider = EP_NAME.lazySequence().singleOrNull()?.takeIf { it.isApplicable(project) }
      if (provider != null) {
        provider.Banner(project, modifier)
        Spacer(modifier = Modifier.height(44.dp))
      }
    }
  }

  fun isApplicable(project: Project): Boolean

  @Composable
  fun Banner(project: Project, modifier: Modifier)
}

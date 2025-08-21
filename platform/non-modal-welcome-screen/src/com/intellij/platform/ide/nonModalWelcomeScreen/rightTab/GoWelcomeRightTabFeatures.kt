package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.goide.statistics.GoWelcomeScreenTabUsageCollector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.features.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.colorPalette

class GoWelcomeRightTabFeatures(private val project: Project) {
  @get:Composable
  internal val featureButtonModels: List<FeatureButtonModel>
    get() = listOfNotNull(
      getFeature(TERMINAL_TOOLWINDOW_FEATURE_KEY)?.let { feature ->
        FeatureButtonModel(
          feature = GoWelcomeScreenTabUsageCollector.Feature.Terminal,
          text = NonModalWelcomeScreenBundle.message("go.non.modal.welcome.screen.right.tab.feature.terminal"),
          icon = feature.icon,
          tint = blueTint,
          onClick = { feature.invoke(project) }
        )
      },
      getFeature(DOCKER_TOOLWINDOW_FEATURE_KEY)?.let { feature ->
        FeatureButtonModel(
          feature = GoWelcomeScreenTabUsageCollector.Feature.Docker,
          text = NonModalWelcomeScreenBundle.message("go.non.modal.welcome.screen.right.tab.feature.docker"),
          icon = feature.icon,
          // TODO: Open the Docker section
          onClick = { feature.invoke(project) }
        )
      },
      getFeature(KUBERNETES_TOOLWINDOW_FEATURE_KEY)?.let { feature ->
        FeatureButtonModel(
          feature = GoWelcomeScreenTabUsageCollector.Feature.Kubernetes,
          text = NonModalWelcomeScreenBundle.message("go.non.modal.welcome.screen.right.tab.feature.kubernetes"),
          icon = feature.icon,
          onClick = { feature.invoke(project) }
        )
      },
      getFeature(HTTP_CLIENT_SCRATCH_FEATURE_KEY)?.let { feature ->
        FeatureButtonModel(
          feature = GoWelcomeScreenTabUsageCollector.Feature.HttpClient,
          text = NonModalWelcomeScreenBundle.message("go.non.modal.welcome.screen.right.tab.feature.http.client"),
          icon = feature.icon,
          tint = blueTint,
          onClick = { feature.invoke(project) }
        )
      },
      getFeature(DATABASE_TOOLWINDOW_FEATURE_KEY)?.let { feature ->
        FeatureButtonModel(
          feature = GoWelcomeScreenTabUsageCollector.Feature.Database,
          text = NonModalWelcomeScreenBundle.message("go.non.modal.welcome.screen.right.tab.feature.database"),
          icon = feature.icon,
          tint = blueTint,
          onClick = { feature.invoke(project) }
        )
      },
      getFeature(PLUGINS_SETTINGS_FEATURE_KEY)?.let { feature ->
        FeatureButtonModel(
          feature = GoWelcomeScreenTabUsageCollector.Feature.Plugins,
          text = NonModalWelcomeScreenBundle.message("go.non.modal.welcome.screen.right.tab.feature.plugins"),
          icon = feature.icon,
          tint = blueTint,
          onClick = { feature.invoke(project) }
        )
      }
    )

  internal data class FeatureButtonModel(
    val feature: GoWelcomeScreenTabUsageCollector.Feature,
    val text: String,
    val icon: IconKey,
    val tint: Color = Color.Unspecified,
    val onClick: () -> Unit,
  )

  private fun getFeature(featureKey: String): GoWelcomeScreenFeatureProvider? {
    val provider = GoWelcomeScreenFeatureProvider.getForFeatureKey(featureKey)
    if (provider == null) {
      thisLogger().warn("Feature provider for the feature key $featureKey not found")
    }
    return provider
  }

  @get:Composable
  private val blueTint: Color
    get() = GoWelcomeRightTab.color(dark = JewelTheme.colorPalette.blueOrNull(8),
                                    light = JewelTheme.colorPalette.blueOrNull(4),
                                    fallback = Color(0xFF548AF7))
}

package com.intellij.platform.ide.nonModalWelcomeScreen

import com.goide.GoIcons
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.GoWelcomeScreenFileIconProvider
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey
import javax.swing.Icon

object GoWelcomeScreenPluginIconProvider {

  private val GO_DOCKER_PLUGIN_ID = PluginId.getId("com.goide.docker")
  private val GO_KUBERNETES_PLUGIN_ID = PluginId.getId("com.intellij.go.kubernetes")

  fun getDockerFallbackIconKey(): IconKey = getFallbackFeatureIconKey("icons/expui/welcomeDockerFallback.svg")

  fun getKubernetesFallbackIconKey(): IconKey = getFallbackFeatureIconKey("icons/welcomeKubernetesFallback.svg")

  fun getDatabaseFallbackIconKey(): IconKey = getFallbackFeatureIconKey("icons/expui/welcomeDatabaseFallback.svg")

  fun getTerminalFallbackIconKey(): IconKey = getFallbackFeatureIconKey("icons/expui/welcomeTerminalFallback.svg")

  private fun getFallbackFeatureIconKey(path: String): IconKey = PathIconKey(path, GoIcons::class.java)

  fun getDockerIcon(): Icon = getPluginIcon(GO_DOCKER_PLUGIN_ID, GoIcons.WELCOME_DOCKER_FALLBACK)

  fun getKubernetesIcon(): Icon = getPluginIcon(GO_KUBERNETES_PLUGIN_ID, GoIcons.WELCOME_KUBERNETES_FALLBACK)

  private fun getPluginIcon(pluginId: PluginId, fallbackIcon: Icon): Icon =
    GoWelcomeScreenFileIconProvider.getForPluginId(pluginId)?.fileIcon ?: fallbackIcon
}

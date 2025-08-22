package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenFileIconProvider
import javax.swing.Icon

object GoWelcomeScreenPluginIconProvider {

  private val GO_DOCKER_PLUGIN_ID = PluginId.getId("com.goide.docker")
  private val GO_KUBERNETES_PLUGIN_ID = PluginId.getId("com.intellij.go.kubernetes")

  fun getDockerIcon(): Icon = getPluginIcon(GO_DOCKER_PLUGIN_ID, AllIcons.Actions.AddDirectory)

  fun getKubernetesIcon(): Icon = getPluginIcon(GO_KUBERNETES_PLUGIN_ID, AllIcons.Actions.AddDirectory)

  private fun getPluginIcon(pluginId: PluginId, fallbackIcon: Icon): Icon =
    WelcomeScreenFileIconProvider.getForPluginId(pluginId)?.fileIcon ?: fallbackIcon
}

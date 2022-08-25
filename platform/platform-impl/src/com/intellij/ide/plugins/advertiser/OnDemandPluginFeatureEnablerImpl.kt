// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.advertiser

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.UnknownFeaturesCollector
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import org.jetbrains.annotations.ApiStatus

private val LOG get() = logger<OnDemandPluginFeatureEnablerImpl>()

@ApiStatus.Experimental
private class OnDemandPluginFeatureEnablerImpl(private val project: Project) : PluginFeatureEnabler {

  override fun enableSuggested() {
    val application = ApplicationManager.getApplication()
    LOG.assertTrue(!application.isDispatchThread && !application.isReadAccessAllowed
                   || application.isUnitTestMode)

    if (!IdeaPluginDescriptorImpl.isOnDemandEnabled) {
      return
    }

    val featureService = PluginFeatureService.instance
    val pluginEnabler = PluginEnabler.getInstance()
    val pluginSet = PluginManagerCore.getPluginSet()

    val descriptors = UnknownFeaturesCollector.getInstance(project)
      .getUnknownFeaturesOfType(DEPENDENCY_SUPPORT_FEATURE)
      .asSequence()
      .mapNotNull { unknownFeature ->
        featureService.getPluginForFeature(
          unknownFeature.featureType,
          unknownFeature.implementationName,
        )
      }.map { it.pluginData.pluginId }
      .filterNot { pluginEnabler.isDisabled(it) }
      .filterNot { pluginSet.isPluginEnabled(it) }
      .mapNotNull { pluginSet.findInstalledPlugin(it) }
      .filter { it.isOnDemand }
      .toList()

    if (!descriptors.isEmpty()) {
      application.invokeAndWait {
        pluginEnabler.enable(descriptors)
      }

      if (!application.isUnitTestMode) {
        application.invokeLater(
          notifyUser(descriptors),
          project.disposed,
        )
      }
    }
  }

  private fun notifyUser(descriptors: List<IdeaPluginDescriptorImpl>) = Runnable {
    val message = IdeBundle.message(
      "plugins.advertiser.enabled.on.demand",
      descriptors.size,
      descriptors.map { it.name },
    )

    notificationGroup.createNotification(message, NotificationType.INFORMATION)
      .setDisplayId("advertiser.enable.on.demand")
      .notify(project)
  }
}
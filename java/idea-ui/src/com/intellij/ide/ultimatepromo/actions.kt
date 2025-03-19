// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ultimatepromo

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PromoAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.tryUltimate
import com.intellij.openapi.util.registry.Registry
import javax.swing.Icon

internal abstract class UltimatePromoAction(private val pluginId: String) : DumbAwareAction(), PromoAction {
  override fun getPromotedProductIcon(): Icon = AllIcons.Ultimate.IdeaUltimatePromo
  override fun getCallToAction(): String {
    return IdeBundle.message("plugin.advertiser.product.call.to.action",
                             PluginAdvertiserService.ideaUltimate.name,
                             IdeBundle.message("plugin.advertiser.free.trial.action"))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("idea.ultimate.features.hints.enabled")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val pluginId = PluginId.getId(pluginId)

    if (project != null) {
      tryUltimate(pluginId, PluginAdvertiserService.ideaUltimate, project, FUSEventSource.ACTIONS)
      return
    }

    FUSEventSource.ACTIONS.openDownloadPageAndLog(e.project, PluginAdvertiserService.ideaUltimate.downloadUrl,
                                                  PluginAdvertiserService.ideaUltimate, pluginId)
  }
}

internal class PromoSpringAction : UltimatePromoAction("com.intellij.spring")
internal class PromoBeansAction : UltimatePromoAction("com.intellij.spring")
internal class PromoPersistenceAction : UltimatePromoAction("com.intellij.javaee.jpa")
internal class PromoEndpointsAction : UltimatePromoAction("com.intellij.microservices.ui")
internal class PromoDatabaseAction : UltimatePromoAction("com.intellij.database")
internal class PromoKubernetesAction : UltimatePromoAction("com.intellij.kubernetes")
internal class PromoOpenAPIAction : UltimatePromoAction("com.intellij.swagger")
internal class PromoProfilerAction : UltimatePromoAction("com.intellij.LineProfiler")

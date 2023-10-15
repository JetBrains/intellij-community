// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.frameworks

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PromoAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService
import javax.swing.Icon

internal abstract class MinimalUltimatePromoAction: AnAction(), PromoAction {
  override fun getPromotedProductIcon(): Icon = AllIcons.Nodes.EnterpriseProject
  override fun getPromotedProductTitle(): String = PluginAdvertiserService.ideaUltimate.name
  override fun getCallToAction(): String = IdeBundle.message("plugin.advertiser.free.trial.action")

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    FUSEventSource.ACTIONS.openDownloadPageAndLog(e.project,
                                                  PluginAdvertiserService.ideaUltimate.downloadUrl,
                                                  PluginId.getId("com.intellij.spring"))
  }
}

internal class PromoSpringAction: MinimalUltimatePromoAction()
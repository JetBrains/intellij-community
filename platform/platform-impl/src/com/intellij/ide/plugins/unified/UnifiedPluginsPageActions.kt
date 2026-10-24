// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.CustomPluginRepositoryService
import com.intellij.ide.plugins.PluginInstallCallbackData
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.certificates.PluginCertificateManager
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerManageAction
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.PluginManagerCustomizer
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.updateSettings.impl.PluginAutoUpdateListener
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.net.HttpProxyConfigurable
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

internal class UnifiedPluginsPageActions @RequiresEdt constructor(
  private val parentComponent: JComponent,
  private val pageScope: CoroutineScope,
  private val host: LegacyPluginUiHost,
  private val customizer: PluginManagerCustomizer?,
  private val onRefreshRequested: () -> Unit,
  private val installedPlugins: () -> List<PluginUiModel>,
  private val onPluginInstalledFromDisk: (PluginInstallCallbackData) -> Unit,
) {
  private var pluginsAutoUpdateEnabled: Boolean? = null

  val group: DefaultActionGroup = createActionGroup()

  fun isModified(): Boolean {
    return pluginsAutoUpdateEnabled?.let { it != UpdateSettings.getInstance().state.isPluginsAutoUpdateEnabled } == true
  }

  fun apply() {
    val enabled = pluginsAutoUpdateEnabled ?: return
    if (UpdateSettings.getInstance().state.isPluginsAutoUpdateEnabled != enabled) {
      UiPluginManager.getInstance().setPluginsAutoUpdateEnabled(enabled)
    }
  }

  fun reset() {
    if (pluginsAutoUpdateEnabled != null) {
      pluginsAutoUpdateEnabled = UpdateSettings.getInstance().state.isPluginsAutoUpdateEnabled
    }
  }

  private fun createActionGroup(): DefaultActionGroup {
    val actions = DefaultActionGroup()
    if (PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()) {
      val state = UpdateSettings.getInstance().state
      pluginsAutoUpdateEnabled = state.isPluginsAutoUpdateEnabled
      ApplicationManager.getApplication().messageBus.connect(pageScope.asDisposable())
        .subscribe(PluginAutoUpdateListener.TOPIC, object : PluginAutoUpdateListener {
          override fun settingsChanged() {
            pluginsAutoUpdateEnabled = state.isPluginsAutoUpdateEnabled
          }
        })
      actions.add(UpdatePluginsAutomaticallyToggleAction())
      actions.addSeparator()
    }

    actions.add(ManagePluginRepositoriesAction())
    actions.add(OpenHttpProxyConfigurableAction())
    actions.addSeparator()
    actions.add(ManagePluginCertificatesAction())
    actions.add(
      host.createInstallFromDiskAction(
        parentComponent,
        beforeAction = {
          PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.INSTALL_FROM_DISK)
        },
        onPluginInstalled = { callbackData ->
          val updateUi = { onPluginInstalledFromDisk(callbackData) }
          if (customizer == null) updateUi() else customizer.updateAfterModification(updateUi)
        },
      )
    )
    customizer?.getExtraPluginsActions()?.let(actions::addAll)
    actions.addSeparator()
    actions.add(ChangePluginStateAction(enable = false))
    actions.add(ChangePluginStateAction(enable = true))

    if (ApplicationManager.getApplication().isInternal) {
      actions.addSeparator()
      actions.add(ResetConfigurableAction())
    }
    return actions
  }

  private inner class ManagePluginRepositoriesAction : DumbAwareAction(IdeBundle.message("plugin.manager.repositories")) {
    override fun actionPerformed(event: AnActionEvent) {
      PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.MANAGE_REPOSITORIES)
      val oldRepoUrls = ArrayList(UpdateSettings.getInstance().storedPluginHosts)
      if (!ShowSettingsUtil.getInstance().editConfigurable(parentComponent, com.intellij.ide.plugins.PluginHostsConfigurable())) return

      CustomPluginRepositoryService.getInstance().clearCache()
      if (customizer == null) {
        onRefreshRequested()
        return
      }

      val newRepoUrls = UpdateSettings.getInstance().storedPluginHosts
      val addedRepoUrls = ArrayList(newRepoUrls).apply { removeAll(oldRepoUrls.toSet()) }
      val removedRepoUrls = ArrayList(oldRepoUrls).apply { removeAll(newRepoUrls.toSet()) }
      customizer.updateCustomRepositories(addedRepoUrls, removedRepoUrls, onRefreshRequested)
    }
  }

  private inner class OpenHttpProxyConfigurableAction : DumbAwareAction(IdeBundle.message("button.http.proxy.settings")) {
    override fun actionPerformed(event: AnActionEvent) {
      PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.HTTP_PROXY)
      if (HttpProxyConfigurable.editConfigurable(parentComponent)) {
        onRefreshRequested()
      }
    }
  }

  private inner class ManagePluginCertificatesAction : DumbAwareAction(IdeBundle.message("plugin.manager.custom.certificates")) {
    override fun actionPerformed(event: AnActionEvent) {
      PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.CERTIFICATES)
      if (ShowSettingsUtil.getInstance().editConfigurable(parentComponent, PluginCertificateManager())) {
        onRefreshRequested()
      }
    }
  }

  private inner class ResetConfigurableAction : DumbAwareAction(IdeBundle.message("plugin.manager.refresh")) {
    override fun actionPerformed(event: AnActionEvent) {
      PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.RESET)
      onRefreshRequested()
    }
  }

  private inner class UpdatePluginsAutomaticallyToggleAction : DumbAwareToggleAction(
    IdeBundle.message("updates.plugins.autoupdate.settings.action")
  ) {
    override fun isSelected(event: AnActionEvent): Boolean = checkNotNull(pluginsAutoUpdateEnabled)

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      PluginManagerUsageCollector.manageActionInvoked(PluginManagerManageAction.TOGGLE_AUTO_UPDATE)
      pluginsAutoUpdateEnabled = state
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class ChangePluginStateAction(private val enable: Boolean) : DumbAwareAction(
    IdeBundle.message(
      if (enable) "plugins.configurable.enable.all.downloaded" else "plugins.configurable.disable.all.downloaded"
    )
  ) {
    override fun actionPerformed(event: AnActionEvent) {
      PluginManagerUsageCollector.manageActionInvoked(
        if (enable) PluginManagerManageAction.ENABLE_ALL else PluginManagerManageAction.DISABLE_ALL
      )
      host.changeAllPluginsState(enable, installedPlugins())
    }
  }
}

internal fun eligibleInstalledPluginModels(sections: List<PluginSectionState>): List<PluginUiModel> {
  val section = sections.firstOrNull { it.id == PluginSectionId.Installed } ?: return emptyList()
  return section.items.map { item ->
    requireNotNull(item.modelHandle) { "Installed plugin ${item.pluginId} has no model handle" }.model
  }
}

internal fun eligibleBundledCategoryPluginModels(
  sections: List<PluginSectionState>,
  category: BundledPluginCategoryGroupState,
): List<PluginUiModel> {
  val section = sections.firstOrNull { it.id == PluginSectionId.Bundled } ?: return emptyList()
  val itemsById = section.items.associateBy(PluginItemState::pluginId)
  return category.pluginIds.map { pluginId ->
    val item = requireNotNull(itemsById[pluginId]) { "Bundled category ${category.category} contains unknown plugin $pluginId" }
    requireNotNull(item.modelHandle) { "Bundled plugin $pluginId has no model handle" }.model
  }
}

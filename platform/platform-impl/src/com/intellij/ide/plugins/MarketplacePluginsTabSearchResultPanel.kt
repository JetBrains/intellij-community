// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.getActiveProject
import com.intellij.ide.plugins.MarketplacePluginsTab.MarketplaceSortByAction
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker.Companion.getInstanceIfEnabled
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector.updateAndGetSearchIndex
import com.intellij.ide.plugins.newui.LinkComponent
import com.intellij.ide.plugins.newui.PluginModelAsyncOperationsExecutor
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ide.plugins.newui.PluginsGroupComponent
import com.intellij.ide.plugins.newui.PluginsGroupComponentWithProgress
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.SearchResultPanel
import com.intellij.ide.plugins.newui.SearchUpDownPopupController
import com.intellij.ide.plugins.newui.UiPluginManager.Companion.getInstance
import com.intellij.ide.plugins.newui.getPluginsViewCustomizer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.findSuggestedPlugins
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Point
import java.awt.event.KeyEvent
import java.util.function.Consumer
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.SwingConstants

@ApiStatus.Internal
internal class MarketplacePluginsTabSearchResultPanel(
  coroutineScope: CoroutineScope,
  marketplaceController: SearchUpDownPopupController,
  panel: PluginsGroupComponentWithProgress,
  private val myProject: Project?,
  private val mySelectionListener: Consumer<in PluginsGroupComponent?>,
  private val myMarketplaceSortByGroup: DefaultActionGroup,
  private val myMarketplacePanelSupplier: Supplier<PluginsGroupComponentWithProgress?>,
) : SearchResultPanel(coroutineScope, marketplaceController, panel, true) {
  private val myMarketplaceSortByAction: LinkComponent

  init {
    myMarketplaceSortByAction = createSortByAction()
  }

  private fun createSortByAction(): LinkComponent {
    val sortByAction: LinkComponent = object : LinkComponent() {
      override fun isInClickableArea(pt: Point?): Boolean {
        return true
      }

      override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
          accessibleContext = AccessibleLinkComponent()
        }
        return accessibleContext
      }

      inner class AccessibleLinkComponent : AccessibleLinkLabel() {
        override fun getAccessibleRole(): AccessibleRole? = AccessibleRole.COMBO_BOX
      }
    }

    sortByAction.setIcon(object : Icon {
      override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        this.icon.paintIcon(c, g, x, y + 1)
      }

      override fun getIconWidth(): Int {
        return this.icon.getIconWidth()
      }

      override fun getIconHeight(): Int {
        return this.icon.getIconHeight()
      }

      val icon: Icon
        get() = AllIcons.General.ButtonDropTriangle
    }) // TODO: icon
    sortByAction.setPaintUnderline(false)
    sortByAction.setIconTextGap(scale(4))
    sortByAction.setHorizontalTextPosition(SwingConstants.LEFT)
    sortByAction.setForeground(PluginsGroupComponent.SECTION_HEADER_FOREGROUND)

    sortByAction.setListener(
      LinkListener { component: LinkLabel<*>?, _: Any? ->
        PluginManagerConfigurablePanel.showRightBottomPopup(
          component!!.getParent().getParent(), IdeBundle.message("plugins.configurable.sort.by"),
          myMarketplaceSortByGroup
        )
      }, null
    )

    DumbAwareAction.create(com.intellij.util.Consumer { _ -> sortByAction.doClick() })
      .registerCustomShortcutSet(KeyEvent.VK_DOWN, 0, sortByAction)
    return sortByAction
  }

  override suspend fun handleQuery(query: String, result: PluginsGroup) {
    val searchIndex = updateAndGetSearchIndex()

    val parser = SearchQueryParser.Marketplace(query)

    if (parser.internal) {
      try {
        val groupDescriptor = getPluginsViewCustomizer().getInternalPluginsGroupDescriptor()
        if (groupDescriptor != null) {
          if (parser.searchQuery == null) {
            result.addDescriptors(groupDescriptor.plugins)
          }
          else {
            for (pluginDescriptor in groupDescriptor.plugins) {
              if (StringUtil.containsIgnoreCase(pluginDescriptor.getName(), parser.searchQuery!!)) {
                result.addDescriptor(pluginDescriptor)
              }
            }
          }
          result.removeDuplicates()
          result.sortByName()
          return
        }
      }
      catch (e: Exception) {
        LOG.error("Error while loading internal plugins group", e)
      }
    }

    PluginModelAsyncOperationsExecutor.getCustomRepositoriesPluginMap().let { customRepositoriesMap ->
      if (parser.suggested && myProject != null) {
        val plugins = findSuggestedPlugins(myProject, customRepositoriesMap)
        result.addModels(plugins)
        updateSearchPanel(result, plugins)
      }
      else if (!parser.repositories.isEmpty()) {
        for (repository in parser.repositories) {
          val descriptors = customRepositoriesMap.get(repository)
          if (descriptors == null) {
            continue
          }
          if (parser.searchQuery == null) {
            result.addModels(descriptors)
          }
          else {
            for (descriptor in descriptors) {
              if (StringUtil.containsIgnoreCase(descriptor.name!!, parser.searchQuery!!)) {
                result.addModel(descriptor)
              }
            }
          }
        }
        result.removeDuplicates()
        result.sortByName()
        updateSearchPanel(result, result.getModels())
      }
      else {
        PluginModelAsyncOperationsExecutor
          .performMarketplaceSearch(parser.urlQuery).let { searchResult ->
            applySearchResult(
              result, searchResult, customRepositoriesMap,
              parser, searchIndex
            )
            updatePanel()
            coroutineScope.launch(Dispatchers.IO) {
              val updates = PluginModelAsyncOperationsExecutor.loadUpdates()
              if (updates.isNotEmpty()) {
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                  PluginManagerConfigurablePanel.applyUpdates(myPanel, updates)
                  mySelectionListener.accept(myMarketplacePanelSupplier.get())
                  mySelectionListener.accept(panel)
                  fullRepaint()
                }
              }
            }
          }
      }
    }
  }

  private suspend fun updateSearchPanel(result: PluginsGroup, plugins: List<PluginUiModel>) {
    val ids = plugins.mapTo(LinkedHashSet()) { it.pluginId }
    result.getPreloadedModel().setInstalledPlugins(getInstance().findInstalledPluginsSync(ids))
    result.getPreloadedModel().setPluginInstallationStates(getInstance().getInstallationStatesSync())
    updatePanel()
  }

  private fun applySearchResult(
    result: PluginsGroup,
    searchResult: PluginSearchResult,
    customRepositoriesMap: Map<String, List<PluginUiModel>>,
    parser: SearchQueryParser.Marketplace,
    searchIndex: Int,
  ) {
    if (searchResult.error != null) {
      ApplicationManager.getApplication().invokeLater(
        Runnable {
          myPanel.getEmptyText()
            .setText(IdeBundle.message("plugins.configurable.search.result.not.loaded"))
            .appendSecondaryText(
              IdeBundle.message("plugins.configurable.check.internet"),
              StatusText.DEFAULT_ATTRIBUTES, null
            )
        }, ModalityState.any()
      )
    }
    // compare plugin versions between marketplace & custom repositories
    val customPlugins: List<PluginUiModel> = ContainerUtil.flatten<PluginUiModel>(customRepositoriesMap.values)
    val plugins =
      RepositoryHelper.mergePluginModelsFromRepositories(
        searchResult.getPlugins(),
        customPlugins,
        false
      )
    result.addModels(0, ArrayList<PluginUiModel?>(plugins))

    if (parser.searchQuery != null) {
      val descriptors = customPlugins.filter { descriptor: PluginUiModel ->
        StringUtil.containsIgnoreCase(
          descriptor.name!!,
          parser.searchQuery!!
        )
      }
      result.addModels(0, descriptors)
    }

    result.removeDuplicates()

    var pluginToScore: Map<PluginUiModel, Double>? = null
    val localRanker = getInstanceIfEnabled()
    if (localRanker != null) {
      pluginToScore = localRanker.rankPlugins(parser, result.getModels())
    }

    if (!result.getModels().isEmpty()) {
      var title = IdeBundle.message("plugin.manager.action.label.sort.by.1")

      for (action in myMarketplaceSortByGroup.getChildren(ActionManager.getInstance())) {
        val sortByAction = action as MarketplaceSortByAction
        sortByAction.setState(parser)
        if (sortByAction.myState) {
          title = IdeBundle.message(
            "plugin.manager.action.label.sort.by",
            sortByAction.myOption.presentableNameSupplier.get()
          )
        }
      }

      myMarketplaceSortByAction.setText(title)
      result.addSecondaryAction(myMarketplaceSortByAction)
    }
    val ids = result.getModels().mapTo(LinkedHashSet()) { it.pluginId }
    result.getPreloadedModel().setInstalledPlugins(getInstance().findInstalledPluginsSync(ids))
    result.getPreloadedModel().setPluginInstallationStates(getInstance().getInstallationStatesSync())
    PluginManagerUsageCollector.performMarketplaceSearch(
      getActiveProject(), parser, result.getModels(),
      searchIndex, pluginToScore
    )
  }

  companion object {
    private val LOG = Logger.getInstance(MarketplacePluginsTabSearchResultPanel::class.java)
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.getActiveProject
import com.intellij.ide.plugins.InstalledPluginsTab.InstalledSearchOptionAction
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.LinkLabelButton
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector.performInstalledTabSearch
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector.updateAndGetSearchIndex
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginModelAsyncOperationsExecutor
import com.intellij.ide.plugins.newui.PluginModelFacade
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ide.plugins.newui.PluginsGroupComponent
import com.intellij.ide.plugins.newui.PluginsGroupComponentWithProgress
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.SearchResultPanel
import com.intellij.ide.plugins.newui.SearchUpDownPopupController
import com.intellij.ide.plugins.newui.UiPluginManager.Companion.getInstance
import com.intellij.ide.plugins.newui.calculateTags
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionListener
import java.util.function.Consumer
import java.util.function.Supplier

@ApiStatus.Internal
internal class InstalledPluginsTabSearchResultPanel(
  coroutineScope: CoroutineScope,
  installedController: SearchUpDownPopupController,
  panel: PluginsGroupComponentWithProgress,
  private val mySearchActionGroup: DefaultActionGroup,
  private val myInstalledPanelSupplier: Supplier<PluginsGroupComponentWithProgress?>,
  private val mySelectionListener: Consumer<in PluginsGroupComponent?>,
  private val mySearchInMarketplaceTabHandler: Consumer<String?>?,
  private val myPluginModelFacade: PluginModelFacade,
) : SearchResultPanel(coroutineScope, installedController, panel, false) {
  override fun setupEmptyText() {
    myPanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.nothing.found"))
    val query = query
    if (query.contains("/downloaded") || query.contains("/userInstalled") ||
        query.contains("/outdated") ||
        query.contains("/enabled") || query.contains("/disabled") ||
        query.contains("/invalid") ||
        query.contains("/bundled") || query.contains("/updatedBundled")
    ) {
      return
    }
    if (mySearchInMarketplaceTabHandler != null) {
      myPanel.getEmptyText().appendSecondaryText(
        IdeBundle.message("plugins.configurable.search.in.marketplace"),
        SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ActionListener { _ -> mySearchInMarketplaceTabHandler.accept(query) })
    }
  }

  override fun setQuery(query: String) {
    super.setQuery(query)
    val parser = SearchQueryParser.Installed(query)
    for (action in mySearchActionGroup.getChildren(ActionManager.getInstance())) {
      (action as InstalledSearchOptionAction).setState(parser)
    }
  }

  override suspend fun handleQuery(query: String, result: PluginsGroup) {
    val searchIndex = updateAndGetSearchIndex()
    myPluginModelFacade.getModel().setInvalidFixCallback(null)
    val parser = SearchQueryParser.Installed(query)
    val descriptors = myPluginModelFacade.getModel().installedDescriptors

    if (!parser.vendors.isEmpty()) {
      val I = descriptors.iterator()
      while (I.hasNext()) {
        if (!MyPluginModel.isVendor(I.next(), parser.vendors)) {
          I.remove()
        }
      }
    }
    if (!parser.tags.isEmpty()) {
      val sessionId = myPluginModelFacade.getModel().sessionId

      val I = descriptors.iterator()
      while (I.hasNext()) {
        if (!ContainerUtil.intersects<String>(I.next().calculateTags(sessionId), parser.tags)) {
          I.remove()
        }
      }
    }
    val I: MutableIterator<PluginUiModel> = descriptors.iterator()
    while (I.hasNext()) {
      val descriptor = I.next()
      if (parser.attributes) {
        if (parser.enabled &&
            (!myPluginModelFacade.isEnabled(descriptor) || !myPluginModelFacade.getErrors(descriptor).isEmpty())
        ) {
          I.remove()
          continue
        }
        if (parser.disabled &&
            (myPluginModelFacade.isEnabled(descriptor) || !myPluginModelFacade.getErrors(descriptor).isEmpty())
        ) {
          I.remove()
          continue
        }
        val isBundledOrBundledUpdate = descriptor.isBundled || descriptor.isBundledUpdate
        if (parser.bundled && !isBundledOrBundledUpdate) {
          I.remove()
          continue
        }
        if (parser.updatedBundled && !descriptor.isBundledUpdate) {
          I.remove()
          continue
        }
        if (parser.userInstalled && isBundledOrBundledUpdate) {
          I.remove()
          continue
        }
        if (parser.invalid && myPluginModelFacade.getErrors(descriptor).isEmpty()) {
          I.remove()
          continue
        }
        if (parser.needUpdate && !getInstance().isNeedUpdate(descriptor.pluginId)) {
          I.remove()
          continue
        }
      }
      if (parser.searchQuery != null && !PluginManagerConfigurablePanel.containsQuery(descriptor, parser.searchQuery)) {
        I.remove()
      }
    }

    result.addModels(descriptors)
    val errors = getInstance()
      .loadErrors(
        myPluginModelFacade.getModel().mySessionId.toString(),
        descriptors.map(PluginUiModel::pluginId)
      )
    result.getPreloadedModel().setErrors(MyPluginModel.getErrors(errors))
    result.getPreloadedModel().setPluginInstallationStates(getInstance().getInstallationStatesSync())
    performInstalledTabSearch(
      getActiveProject(), parser, result.getModels(), searchIndex, null
    )

    if (!result.getModels().isEmpty()) {
      if (parser.invalid) {
        myPluginModelFacade.getModel().setInvalidFixCallback(Runnable {
          val group = group
          if (group.ui == null) {
            myPluginModelFacade.getModel().setInvalidFixCallback(null)
            return@Runnable
          }

          val resultPanel = panel

          for (descriptor in ArrayList<PluginUiModel>(group.getModels())) {
            if (myPluginModelFacade.getErrors(descriptor).isEmpty()) {
              resultPanel.removeFromGroup(group, descriptor)
            }
          }

          group.titleWithCount()
          fullRepaint()
          if (group.getModels().isEmpty()) {
            myPluginModelFacade.getModel().setInvalidFixCallback(null)
            removeGroup()
          }
        })
      }
      else if (parser.needUpdate) {
        result.mainAction = LinkLabelButton<Any?>(
          IdeBundle.message("plugin.manager.update.all"),
          null,
          LinkListener { _, _ ->
            result.mainAction!!.setEnabled(false)
            for (plugin in result.ui.plugins) {
              plugin.updatePlugin()
            }
          })
      }
      coroutineScope.launch {
        PluginModelAsyncOperationsExecutor.loadUpdates().let { updates ->
          if (!ContainerUtil.isEmpty(updates)) {
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              PluginManagerConfigurablePanel.applyUpdates(myPanel, updates)
              mySelectionListener.accept(myInstalledPanelSupplier.get())
              mySelectionListener.accept(panel)
              fullRepaint()
            }
          }
        }
      }
    }
    updatePanel()
  }
}

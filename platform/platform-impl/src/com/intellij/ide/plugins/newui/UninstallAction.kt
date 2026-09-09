// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.util.function.Function
import javax.swing.JComponent

internal class UninstallAction<C : JComponent>(
  private val operationLauncher: PluginOperationLauncher,
  pluginModelFacade: PluginModelFacade,
  showShortcut: Boolean,
  private val operationUi: PluginOperationUiHandle,
  selection: MutableList<C>,
  pluginModelGetter: Function<C, PluginUiModel?>,
) : SelectionBasedPluginModelAction<C?>(
  getText(selection, pluginModelGetter),
  pluginModelFacade,
  showShortcut,
  selection,
  pluginModelGetter
) {
  private val myDynamicTitle: Boolean = selection.size == 1 && pluginModelGetter.apply(selection.first()) == null

  override fun update(e: AnActionEvent) {
    val descriptors = allDescriptors

    if (myDynamicTitle) {
      val uiModel = descriptors.first()
      e.presentation.text = IdeBundle.message(
        if (descriptors.size == 1 && uiModel.isBundledUpdate)
          "plugins.configurable.uninstall.bundled.update"
        else
          "plugins.configurable.uninstall"
      )
    }

    val disabled = descriptors.isEmpty() ||
                   descriptors.any { it.isBundled } ||
                   descriptors.any { myPluginModelFacade.isUninstalled(it.pluginId) }

    e.presentation.setEnabledAndVisible(!disabled)
    setShortcutSet(SHORTCUT_SET, myShowShortcut)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val models = allDescriptors.toList()
    val modelFacade = myPluginModelFacade
    val operationUi = operationUi
    val operationContext = operationUi.captureContext()
    operationLauncher.launch(Dispatchers.EDT + operationContext.modalityState.asContextElement()) {
      uninstallPlugins(modelFacade, operationContext, models)
    }
  }

  companion object {
    private val SHORTCUT_SET: ShortcutSet = EventHandler.getShortcuts(IdeActions.ACTION_EDITOR_DELETE)
                                            ?: CustomShortcutSet(EventHandler.DELETE_CODE)
  }

}

private suspend fun uninstallPlugins(
  modelFacade: PluginModelFacade,
  operationUi: PluginOperationUiContext,
  models: List<PluginUiModel>,
) {
  val toDeleteWithAsk = mutableListOf<PluginUiModel>()
  val toDelete = mutableListOf<PluginUiModel>()
  val pluginIds = models.map { it.pluginId }
  val prepareToUninstallResult = withContext(Dispatchers.EDT) {
    UiPluginManager.getInstance().prepareToUninstall(pluginIds)
  }

  for (model in models) {
    val dependents = prepareToUninstallResult.dependants[model.pluginId]?.map { it.name } ?: emptyList()
    if (dependents.isEmpty()) {
      toDeleteWithAsk.add(model)
    }
    else {
      val bundledUpdate = prepareToUninstallResult.isPluginBundled(model.pluginId)
      val message = getUninstallDependentsMessage(model, dependents, bundledUpdate)
      if (askToUninstall(message, operationUi, bundledUpdate)) {
        toDelete.add(model)
      }
    }
  }

  if (toDeleteWithAsk.isNotEmpty()) {
    val bundledUpdate = toDeleteWithAsk.size == 1 && prepareToUninstallResult.isPluginBundled(toDeleteWithAsk.first().pluginId)
    val message = getUninstallAllMessage(toDeleteWithAsk, bundledUpdate)
    if (askToUninstall(message, operationUi, bundledUpdate)) {
      toDelete.addAll(toDeleteWithAsk)
    }
  }

  for (model in toDelete) {
    uninstallAndUpdateUi(modelFacade, model)
  }
}

private suspend fun uninstallAndUpdateUi(modelFacade: PluginModelFacade, model: PluginUiModel) {
  val pluginManagerCustomizer = PluginManagerCustomizer.getInstance()
  if (pluginManagerCustomizer == null) {
    modelFacade.uninstallAndUpdateUi(model)
    return
  }
  pluginManagerCustomizer.getUninstallButtonCustomizationModel(modelFacade, model)?.action()
}

private fun getUninstallAllMessage(descriptors: Collection<PluginUiModel>, bundledUpdate: Boolean): @Nls String {
  return if (descriptors.size == 1) {
    val descriptor = descriptors.first()
    IdeBundle.message("prompt.uninstall.plugin", descriptor.name, if (bundledUpdate) 1 else 0)
  }
  else {
    IdeBundle.message("prompt.uninstall.several.plugins", descriptors.size)
  }
}

private fun getUninstallDependentsMessage(
  descriptor: PluginUiModel,
  dependents: List<String>,
  bundledUpdate: Boolean,
): @Nls String {
  val listOfDeps = StringUtil.join(
    dependents,
    { plugin -> "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;$plugin" },
    "<br>"
  )
  val message = IdeBundle.message(
    "dialog.message.following.plugin.depend.on",
    dependents.size,
    descriptor.name,
    listOfDeps,
    if (bundledUpdate) 1 else 0
  )
  return XmlStringUtil.wrapInHtml(message)
}

private fun askToUninstall(
  message: @Nls String,
  operationUi: PluginOperationUiContext,
  bundledUpdate: Boolean,
): Boolean {
  val parentComponent = operationUi.getParentComponent() ?: return false
  return MessageDialogBuilder.yesNo(
    IdeBundle.message("title.plugin.uninstall", if (bundledUpdate) 1 else 0),
    message
  ).ask(parentComponent)
}

@Suppress("UNCHECKED_CAST")
private fun <C> isBundledUpdate(selection: MutableList<C>, pluginDescriptor: Function<C, PluginUiModel?>): Boolean {
  return selection
    .mapNotNull { element -> pluginDescriptor.apply(element) }
    .all(PluginUiModel::isBundledUpdate)
}

private fun <C> getText(
  selection: MutableList<C>,
  pluginModelGetter: Function<C, PluginUiModel?>,
): @Nls String = IdeBundle.message(
  if (isBundledUpdate(selection, pluginModelGetter))
    "plugins.configurable.uninstall.bundled.update"
  else
    "plugins.configurable.uninstall"
)

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.util.function.Function
import javax.swing.JComponent

internal class UninstallAction<C : JComponent>(
  private val coroutineScope: CoroutineScope,
  pluginModelFacade: PluginModelFacade,
  showShortcut: Boolean,
  private val myUiParent: JComponent,
  selection: MutableList<C>,
  pluginModelGetter: Function<C, PluginUiModel?>,
  private val myOnFinishAction: Runnable,
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
    coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(myUiParent).asContextElement()) {
      val selection = getSelection()

      val toDeleteWithAsk = mutableListOf<PluginUiModel>()
      val toDelete = mutableListOf<PluginUiModel>()

      val pluginIds = selection.values.map { it.pluginId }
      val prepareToUninstallResult = withContext(Dispatchers.EDT) { UiPluginManager.getInstance().prepareToUninstall(pluginIds) }

      for ((component, model) in selection) {
        val dependents = prepareToUninstallResult.dependants[model.pluginId]?.map { it.name } ?: emptyList()

        if (dependents.isEmpty()) {
          toDeleteWithAsk.add(model)
        }
        else {
          val bundledUpdate = prepareToUninstallResult.isPluginBundled(model.pluginId)
          if (askToUninstall(getUninstallDependentsMessage(model, dependents, bundledUpdate), component!!, bundledUpdate)) {
            toDelete.add(model)
          }
        }
      }

      var runFinishAction = false

      if (toDeleteWithAsk.isNotEmpty()) {
        val bundledUpdate = toDeleteWithAsk.size == 1 && prepareToUninstallResult.isPluginBundled(toDeleteWithAsk.first().pluginId)
        if (askToUninstall(getUninstallAllMessage(toDeleteWithAsk, bundledUpdate), myUiParent, bundledUpdate)) {
          for (model in toDeleteWithAsk) {
            myPluginModelFacade.uninstallAndUpdateUi(model)
          }
          runFinishAction = true
        }
      }

      for (model in toDelete) {
        myPluginModelFacade.uninstallAndUpdateUi(model)
      }

      if (runFinishAction || toDelete.isNotEmpty()) {
        myOnFinishAction.run()
      }
    }
  }

  companion object {
    private val SHORTCUT_SET: ShortcutSet = EventHandler.getShortcuts(IdeActions.ACTION_EDITOR_DELETE)
                                            ?: CustomShortcutSet(EventHandler.DELETE_CODE)

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

    private fun askToUninstall(message: @Nls String, parentComponent: JComponent, bundledUpdate: Boolean): Boolean {
      return MessageDialogBuilder.yesNo(
        IdeBundle.message("title.plugin.uninstall", if (bundledUpdate) 1 else 0),
        message
      ).ask(parentComponent)
    }

  }

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

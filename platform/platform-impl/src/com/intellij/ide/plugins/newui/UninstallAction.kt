// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.marketplace.PluginNameAndId
import com.intellij.ide.plugins.newui.UiPluginManager.Companion.getInstance
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNo
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.xml.util.XmlStringUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.function.Function
import javax.swing.JComponent

internal class UninstallAction<C : JComponent?>(
  pluginModelFacade: PluginModelFacade,
  showShortcut: Boolean,
  private val myUiParent: JComponent,
  selection: MutableList<out C?>,
  pluginModelGetter: Function<in C?, PluginUiModel?>,
  private val myOnFinishAction: Runnable
) : SelectionBasedPluginModelAction<C?>(
  IdeBundle.message(if (isBundledUpdate(selection, pluginModelGetter as Function<Any?, PluginUiModel?>))
                      "plugins.configurable.uninstall.bundled.update"
                    else
                      "plugins.configurable.uninstall"),
  pluginModelFacade,
  showShortcut,
  selection,
  pluginModelGetter) {
  private val myDynamicTitle: Boolean

  init {
    myDynamicTitle = selection.size == 1 && pluginModelGetter.apply(selection.iterator().next()) == null
  }

  override fun update(e: AnActionEvent) {
    val descriptors = getAllDescriptors()

    if (myDynamicTitle) {
      val uiModel = descriptors.iterator().next()
      e.getPresentation().setText(IdeBundle.message(
        if (descriptors.size == 1 && uiModel.isBundledUpdate)
          "plugins.configurable.uninstall.bundled.update"
        else
          "plugins.configurable.uninstall"))
    }

    val disabled = descriptors.isEmpty() ||
                   ContainerUtil.exists<PluginUiModel?>(descriptors, PluginUiModel::isBundled) ||
                   ContainerUtil.exists<PluginUiModel?>(descriptors, Condition { it: PluginUiModel? ->
                     myPluginModelFacade.isUninstalled(
                       it!!.pluginId)
                   })
    e.getPresentation().setEnabledAndVisible(!disabled)

    setShortcutSet(SHORTCUT_SET, myShowShortcut)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val selection = getSelection()

    val toDeleteWithAsk: MutableList<PluginUiModel> = ArrayList<PluginUiModel>()
    val toDelete: MutableList<PluginUiModel> = ArrayList<PluginUiModel>()

    val pluginIds = ContainerUtil.map<PluginUiModel?, PluginId?>(selection.values, PluginUiModel::pluginId)
    val prepareToUninstallResult = getInstance().prepareToUninstall(pluginIds)
    for (entry in selection.entries) {
      val model: PluginUiModel = entry.value!!
      val dependents: MutableList<String?> = ContainerUtil.map<PluginNameAndId, String?>(
        prepareToUninstallResult.dependants.get(model.pluginId), com.intellij.util.Function { it: PluginNameAndId -> it.name })
      if (dependents.isEmpty()) {
        toDeleteWithAsk.add(model)
      }
      else {
        val bundledUpdate = prepareToUninstallResult.isPluginBundled(model.pluginId)
        if (Companion.askToUninstall(getUninstallDependentsMessage(model, dependents, bundledUpdate), entry.key!!, bundledUpdate)) {
          toDelete.add(model)
        }
      }
    }

    var runFinishAction = false

    if (!toDeleteWithAsk.isEmpty()) {
      val bundledUpdate = toDeleteWithAsk.size == 1
                          && prepareToUninstallResult.isPluginBundled(toDeleteWithAsk.get(0).pluginId)
      if (askToUninstall(getUninstallAllMessage(toDeleteWithAsk, bundledUpdate), myUiParent, bundledUpdate)) {
        for (descriptor in toDeleteWithAsk) {
          myPluginModelFacade.uninstallAndUpdateUi(descriptor)
        }
        runFinishAction = true
      }
    }

    for (descriptor in toDelete) {
      myPluginModelFacade.uninstallAndUpdateUi(descriptor)
    }

    if (runFinishAction || !toDelete.isEmpty()) {
      myOnFinishAction.run()
    }
  }

  companion object {
    private val SHORTCUT_SET: ShortcutSet

    init {
      val deleteShortcutSet = EventHandler.getShortcuts(IdeActions.ACTION_EDITOR_DELETE)
      SHORTCUT_SET = if (deleteShortcutSet != null) deleteShortcutSet else CustomShortcutSet(EventHandler.DELETE_CODE)
    }

    private fun isBundledUpdate(selection: MutableList<*>, pluginDescriptor: Function<Any?, PluginUiModel?>?): Boolean {
      return StreamEx.of(selection)
        .map<PluginUiModel?>(pluginDescriptor)
        .filter { obj: PluginUiModel? -> Objects.nonNull(obj) }
        .allMatch(PluginUiModel::isBundledUpdate)
    }

    private fun getUninstallAllMessage(descriptors: MutableCollection<PluginUiModel>, bundledUpdate: Boolean): @Nls String {
      if (descriptors.size == 1) {
        val descriptor = descriptors.iterator().next()
        return IdeBundle.message("prompt.uninstall.plugin", descriptor.name, if (bundledUpdate) 1 else 0)
      }
      return IdeBundle.message("prompt.uninstall.several.plugins", descriptors.size)
    }

    private fun getUninstallDependentsMessage(
      descriptor: PluginUiModel,
      dependents: MutableList<String?>,
      bundledUpdate: Boolean
    ): @Nls String {
      val listOfDeps: String?
      String > StringUtil.join<String?>(dependents,
                                        com.intellij.util.Function { plugin: String? -> "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + plugin },
                                        "<br>")
      val message = IdeBundle.message("dialog.message.following.plugin.depend.on",
                                      dependents.size,
                                      descriptor.name,
                                      listOfDeps,
                                      if (bundledUpdate) 1 else 0)
      return XmlStringUtil.wrapInHtml(message)
    }

    private fun askToUninstall(message: @Nls String, parentComponent: JComponent, bundledUpdate: Boolean): Boolean {
      return yesNo(IdeBundle.message("title.plugin.uninstall", if (bundledUpdate) 1 else 0), message).ask(parentComponent)
    }
  }
}

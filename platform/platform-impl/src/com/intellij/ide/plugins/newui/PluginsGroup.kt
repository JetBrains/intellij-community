// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
open class PluginsGroup(
  @JvmField var title: @Nls String,
  @JvmField val type: PluginsGroupType,
) {
  @JvmField protected val myTitlePrefix: @Nls String = title
  @JvmField var titleLabel: JLabel? = null

  /** if `mainAction` is not null, it is shown. Otherwise, `secondaryActions` are shown */
  @JvmField var mainAction: LinkLabel<Any?>? = null
  @JvmField var secondaryActions: MutableList<JComponent>? = null
  @JvmField var ui: UIPluginGroup? = null
  @JvmField var clearCallback: Runnable? = null
  @JvmField protected val models: MutableList<PluginUiModel> = ArrayList()
  private val preloadedModel: ListPluginModel = ListPluginModel()
  @JvmField var promotionPanel: JComponent? = null

  open fun clear() {
    ui = null
    models.clear()
    titleLabel = null
    mainAction = null
    secondaryActions = null
    promotionPanel = null
    if (clearCallback != null) {
      clearCallback!!.run()
      clearCallback = null
    }
  }

  open fun addSecondaryAction(component: JComponent) {
    if (secondaryActions == null) {
      secondaryActions = ArrayList()
    }
    secondaryActions!!.add(component)
  }

  open fun setPromotionPanel(panel: JComponent) {
    promotionPanel = panel
  }

  open fun titleWithCount() {
    title = myTitlePrefix + " (" + models.size + ")"
    updateTitle()
  }

  open fun titleWithEnabled(pluginModelFacade: PluginModelFacade) {
    var enabled = 0
    for (descriptor in models) {
      if (pluginModelFacade.isLoaded(descriptor) &&
          pluginModelFacade.isEnabled(descriptor) &&
          !descriptor.isIncompatible
      ) {
        enabled++
      }
    }
    titleWithCount(enabled)
  }

  open fun titleWithCount(enabled: Int) {
    title = IdeBundle.message("plugins.configurable.title.with.count", myTitlePrefix, enabled, models.size)
    updateTitle()
  }

  open fun getPluginIndex(pluginId: PluginId): Int {
    return models.indexOfFirst { it.pluginId == pluginId }
  }

  open fun getPreloadedModel(): ListPluginModel {
    return preloadedModel
  }

  protected open fun updateTitle() {
    if (titleLabel != null) {
      titleLabel!!.text = title
    }
  }

  open fun addWithIndex(model: PluginUiModel): Int {
    models.add(model)
    sortByName()
    return models.indexOf(model)
  }

  @Deprecated("Use addModel(PluginUiModelAdapter(descriptor))", replaceWith = ReplaceWith("addModel(PluginUiModelAdapter(descriptor))"))
  open fun addDescriptor(descriptor: IdeaPluginDescriptor) {
    models.add(PluginUiModelAdapter(descriptor))
  }

  open fun addModel(model: PluginUiModel) {
    models.add(model)
  }

  @Deprecated(
    "Use addModels(descriptors.map(::PluginUiModelAdapter))",
    replaceWith = ReplaceWith("addModels(descriptors.map(::PluginUiModelAdapter))"),
  )
  open fun addDescriptors(descriptors: Collection<IdeaPluginDescriptor>) {
    models.addAll(descriptors.map(::PluginUiModelAdapter))
  }

  open fun addModels(models: Collection<PluginUiModel>) {
    this.models.addAll(models)
  }

  open fun addModels(index: Int, models: Collection<PluginUiModel>) {
    this.models.addAll(index, models)
  }

  open fun removeDescriptor(model: PluginUiModel) {
    models.remove(model)
  }

  @Deprecated(
    "Use models.map(PluginUiModel::descriptor)",
    replaceWith = ReplaceWith("models.map(PluginUiModel::descriptor)"),
  )
  open fun getDescriptors(): List<IdeaPluginDescriptor> {
    return models.map { it.getDescriptor() }
  }

  open fun getModels(): MutableList<PluginUiModel> {
    return models
  }

  open fun removeDuplicates() {
    ContainerUtil.removeDuplicates(models)
  }

  open fun sortByName() {
    sortByName(models)
  }

  companion object {
    @JvmStatic
    fun sortByName(models: MutableList<PluginUiModel>) {
      ContainerUtil.sort(models) { o1, o2 -> StringUtil.compare(o1.name, o2.name, true) }
    }
  }
}

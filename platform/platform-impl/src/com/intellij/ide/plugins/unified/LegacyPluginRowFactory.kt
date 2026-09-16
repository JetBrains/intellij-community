// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginsGroupType
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.PluginPreparedUpdateState
import com.intellij.ide.plugins.newui.PluginProgressState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginRowRenderKey
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.Nls

internal class LegacyPluginRowFactory @RequiresEdt(generateAssertion = false /* IJPL-115548 */) constructor(
  private val host: LegacyPluginUiHost,
  private val listModel: ListPluginModel,
  private val searchListener: LinkListener<Any>,
  onSelectionChanged: (List<PluginOccurrenceId>) -> Unit,
) : PluginRowFactory {
  private val sectionContexts = HashMap<PluginSectionId, SectionContext>()
  private val eventHandler = UnifiedPluginRowEventHandler(onSelectionChanged)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun specification(section: PluginSectionState, item: PluginItemState): PluginRowSpecification<Any> {
    val context = sectionContext(section)
    val model = requireNotNull(item.modelHandle) { "Plugin ${item.pluginId} has no model handle" }.model
    val renderKey = item.rowInput?.let { input ->
      host.createRowRenderKey(model, context.group, context.presentation.marketplace, input)
    } ?: host.createRowRenderKey(model, context.group, listModel, context.presentation.marketplace)
    return PluginRowSpecification(
      occurrenceId = section.occurrenceId(item.pluginId),
      item = item,
      renderKey = renderKey,
    )
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun createRow(occurrenceId: PluginOccurrenceId, item: PluginItemState, renderKey: Any): PluginRow {
    val context = checkNotNull(sectionContexts[occurrenceId.sectionId]) {
      "Section ${occurrenceId.sectionId} must be prepared before creating rows"
    }
    val model = requireNotNull(item.modelHandle) { "Plugin ${item.pluginId} has no model handle" }.model
    val component = host.createRow(
      model,
      context.group,
      listModel,
      searchListener,
      context.presentation.marketplace,
      renderKey as PluginRowRenderKey,
      registerInstallingWithoutGroup = occurrenceId.sectionId == PluginSectionId.Installing,
    )
    try {
      eventHandler.register(occurrenceId, component)
    }
    catch (t: Throwable) {
      host.releaseRow(component)
      throw t
    }
    return LegacyPluginRow(component, eventHandler, host)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun rowsRendered(bindings: List<PluginRowBinding<PluginRow>>) {
    bindings.forEach { binding ->
      val row = binding.row as LegacyPluginRow
      row.renderInput(binding.item.rowInput)
    }
    eventHandler.renderRows(
      bindings.map { binding -> binding.occurrenceId to (binding.row as LegacyPluginRow).component },
    )
  }

  private fun sectionContext(section: PluginSectionState): SectionContext {
    val presentation = legacyRowPresentation(section.id)
    val context = sectionContexts.getOrPut(section.id) {
      SectionContext(PluginsGroup(pluginSectionTitle(section), presentation.groupType), presentation)
    }
    check(context.presentation == presentation) { "Section presentation changed for ${section.id}" }
    context.group.title = pluginSectionTitle(section)
    context.updateItems(section.items)
    return context
  }

  private data class SectionContext(
    val group: PluginsGroup,
    val presentation: LegacyPluginRowPresentation,
  ) {
    private var items: List<PluginItemState>? = null

    fun updateItems(updatedItems: List<PluginItemState>) {
      if (items === updatedItems) return
      items = updatedItems
      group.getModels().apply {
        clear()
        updatedItems.mapNotNullTo(this) { it.modelHandle?.model }
      }
    }
  }
}

internal class LegacyPluginRow(
  override val component: ListPluginComponent,
  private val eventHandler: UnifiedPluginRowEventHandler,
  private val host: LegacyPluginUiHost,
) : PluginRow {
  private var closed = false
  private var renderedInput: PluginRowInput? = null

  val detailsProgress: PluginProgressState?
    get() = renderedInput?.detailsProgress
            ?: if (component.underProgress()) PluginProgressState.Indeterminate else null

  val preparedUpdate: PluginPreparedUpdateState?
    get() = renderedInput?.preparedUpdate

  fun renderInput(input: PluginRowInput?) {
    if (input == null || input == renderedInput) return
    if (input.operationInProgress) {
      component.showReadOnlyProgress()
    }
    else if (component.underProgress()) {
      component.hideProgress()
    }
    component.updateButtons(input.installedPlugin, input.installationState)
    component.updateErrors(input.errors)
    component.setUpdateDescriptor(input.updateDescriptor)
    input.preparedUpdate?.let { prepared ->
      component.pluginInstalled(true, prepared.restartRequired, input.installedPlugin)
    }
    renderedInput = input
  }

  override fun renderSelection(selected: Boolean) {
    component.setSelection(
      if (selected) com.intellij.ide.plugins.newui.EventHandler.SelectionType.SELECTION
      else com.intellij.ide.plugins.newui.EventHandler.SelectionType.NONE,
      false,
    )
  }

  override fun close() {
    if (closed) return
    closed = true
    eventHandler.unregister(component)
    host.releaseRow(component)
  }
}

internal data class LegacyPluginRowPresentation(
  val groupType: PluginsGroupType,
  val marketplace: Boolean,
)

internal fun legacyRowPresentation(sectionId: PluginSectionId): LegacyPluginRowPresentation {
  return when (sectionId) {
    PluginSectionId.Installing -> LegacyPluginRowPresentation(PluginsGroupType.INSTALLING, marketplace = false)
    PluginSectionId.Installed, PluginSectionId.Bundled ->
      LegacyPluginRowPresentation(PluginsGroupType.INSTALLED, marketplace = false)
    PluginSectionId.Internal -> LegacyPluginRowPresentation(PluginsGroupType.INTERNAL, marketplace = true)
    PluginSectionId.Suggested -> LegacyPluginRowPresentation(PluginsGroupType.SUGGESTED, marketplace = true)
    PluginSectionId.Marketplace -> LegacyPluginRowPresentation(PluginsGroupType.SEARCH, marketplace = true)
    PluginSectionId.CustomRepositoryCatalog -> LegacyPluginRowPresentation(PluginsGroupType.CUSTOM_REPOSITORY, marketplace = true)
    is PluginSectionId.CustomRepository -> LegacyPluginRowPresentation(PluginsGroupType.CUSTOM_REPOSITORY, marketplace = true)
  }
}

internal fun pluginSectionTitle(section: PluginSectionState): @Nls String {
  return when (val id = section.id) {
    PluginSectionId.Installing -> IdeBundle.message("plugins.configurable.installing")
    PluginSectionId.Installed -> IdeBundle.message("plugin.manager.tab.installed")
    PluginSectionId.Bundled -> IdeBundle.message("plugins.configurable.bundled")
    PluginSectionId.Internal -> checkNotNull(section.title) { "Internal plugin section must have a title" }
    PluginSectionId.Suggested -> IdeBundle.message("plugins.configurable.suggested")
    PluginSectionId.Marketplace -> IdeBundle.message("plugin.manager.tab.marketplace")
    PluginSectionId.CustomRepositoryCatalog -> IdeBundle.message("configurable.PluginHostsConfigurable.display.name")
    is PluginSectionId.CustomRepository -> section.title ?: IdeBundle.message("plugins.configurable.repository.0", id.repositoryId)
  }
}

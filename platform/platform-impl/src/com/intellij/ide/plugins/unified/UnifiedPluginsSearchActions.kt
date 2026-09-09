// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CheckedActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

internal fun createUnifiedPluginFilterActionGroup(
  state: UnifiedPluginsSearchControlsState,
  onIntent: (UnifiedPluginSearchControlIntent) -> Unit,
): DefaultActionGroup {
  return FilterActionGroup().apply {
    add(createFacetGroup(
      IdeBundle.message("plugins.configurable.filter.tag"),
      state.options.tags,
      state.selectedTags,
      state.options.facetValuesLoading,
    ) { value, selected ->
      onIntent(UnifiedPluginSearchControlIntent.ToggleAttribute(UnifiedPluginQueryAttribute.Tag, value, selected))
    })
    if (state.options.repositories.isNotEmpty()) {
      add(createFacetGroup(
        IdeBundle.message("plugins.configurable.filter.repository"),
        state.options.repositories,
        state.selectedRepositories,
        state.options.repositoriesLoading,
        shortenValues = true,
      ) { value, selected ->
        onIntent(UnifiedPluginSearchControlIntent.ToggleAttribute(UnifiedPluginQueryAttribute.Repository, value, selected))
      })
    }
    add(Separator(IdeBundle.message("plugins.configurable.filter.installed")))
    add(createFacetGroup(
      IdeBundle.message("plugins.configurable.filter.vendor"),
      state.options.vendors,
      state.selectedVendors,
      state.options.facetValuesLoading,
    ) { value, selected ->
      onIntent(UnifiedPluginSearchControlIntent.ToggleAttribute(UnifiedPluginQueryAttribute.Vendor, value, selected))
    })
    add(createFacetGroup(
      IdeBundle.message("plugins.configurable.filter.category"),
      state.options.categories,
      state.selectedCategories,
      state.options.facetValuesLoading,
    ) { value, selected ->
      onIntent(UnifiedPluginSearchControlIntent.ToggleAttribute(UnifiedPluginQueryAttribute.Category, value, selected))
    })
    val selection = ExclusiveSelection(state.selectedInstalledFilter)
    INSTALLED_FILTERS.forEach { filter ->
      add(ExclusiveToggleAction(
        text = installedFilterText(filter),
        value = filter,
        selection = selection,
        allowEmpty = true,
        onSelected = { selected ->
          onIntent(UnifiedPluginSearchControlIntent.ToggleInstalledFilter(filter, selected))
        },
      ))
    }
  }
}

internal fun createUnifiedPluginSortActionGroup(
  state: UnifiedPluginsSearchControlsState,
  onIntent: (UnifiedPluginSearchControlIntent) -> Unit,
): DefaultActionGroup {
  return SortActionGroup().apply {
    val selection = ExclusiveSelection(state.effectiveSort)
    SORT_OPTIONS.forEach { sort ->
      add(ExclusiveToggleAction(
        text = sort.presentableNameSupplier.get(),
        value = sort,
        selection = selection,
        allowEmpty = false,
        onSelected = { selected ->
          if (selected) onIntent(UnifiedPluginSearchControlIntent.SelectSort(sort))
        },
      ))
    }
  }
}

private fun createFacetGroup(
  title: @Nls String,
  values: List<String>,
  selectedValues: Set<String>,
  loading: Boolean,
  shortenValues: Boolean = false,
  onSelected: (String, Boolean) -> Unit,
): DefaultActionGroup {
  return DefaultActionGroup(title, true).apply {
    if (values.isEmpty()) {
      add(DisabledAction(IdeBundle.message(
        if (loading) "plugins.configurable.filter.options.loading" else "plugins.configurable.filter.options.empty"
      )))
    }
    else {
      values.forEach { value ->
        val text = if (shortenValues) shortenFilterValue(value) else value
        add(FilterToggleAction(
          text = text,
          description = value.takeIf { it != text },
          selected = value in selectedValues,
          onSelected = { selected -> onSelected(value, selected) },
        ))
      }
    }
  }
}

internal fun shortenFilterValue(value: String): String {
  if (value.length <= MAX_FILTER_VALUE_LENGTH) return value
  val retainedLength = MAX_FILTER_VALUE_LENGTH - ELLIPSIS.length
  val prefixLength = retainedLength / 2
  return value.take(prefixLength) + ELLIPSIS + value.takeLast(retainedLength - prefixLength)
}

private class FilterToggleAction(
  text: @NlsSafe String,
  description: @NlsSafe String? = null,
  private var selected: Boolean,
  private val onSelected: (Boolean) -> Unit,
) : DumbAwareToggleAction(text, description, null) {
  init {
    templatePresentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, description)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = selected

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (selected == state) return
    selected = state
    onSelected(state)
  }
}

private class ExclusiveSelection<T : Any>(var value: T?)

private class ExclusiveToggleAction<T : Any>(
  text: @NlsSafe String,
  private val value: T,
  private val selection: ExclusiveSelection<T>,
  private val allowEmpty: Boolean,
  private val onSelected: (Boolean) -> Unit,
) : DumbAwareToggleAction(text) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean = selection.value == value

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state == isSelected(e) || !state && !allowEmpty) return
    selection.value = value.takeIf { state }
    onSelected(state)
  }
}

private class DisabledAction(text: @Nls String) : DumbAwareAction(text) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
  }

  override fun actionPerformed(e: AnActionEvent) = Unit
}

private class SortActionGroup : DefaultActionGroup(), CheckedActionGroup

private class FilterActionGroup : DefaultActionGroup(), CheckedActionGroup

private fun installedFilterText(filter: UnifiedPluginInstalledFilter): String {
  val key = when (filter) {
    UnifiedPluginInstalledFilter.UpdateAvailable -> "plugins.configurable.InstalledSearchOption.NeedUpdate"
    UnifiedPluginInstalledFilter.Enabled -> "plugins.configurable.InstalledSearchOption.Enabled"
    UnifiedPluginInstalledFilter.Disabled -> "plugins.configurable.InstalledSearchOption.Disabled"
    UnifiedPluginInstalledFilter.Invalid -> "plugins.configurable.InstalledSearchOption.Invalid"
    UnifiedPluginInstalledFilter.UpdatedBundled -> "plugins.configurable.InstalledSearchOption.UpdatedBundled"
  }
  return IdeBundle.message(key)
}

private val INSTALLED_FILTERS = listOf(
  UnifiedPluginInstalledFilter.UpdateAvailable,
  UnifiedPluginInstalledFilter.Enabled,
  UnifiedPluginInstalledFilter.Disabled,
  UnifiedPluginInstalledFilter.Invalid,
  UnifiedPluginInstalledFilter.UpdatedBundled,
)

private val SORT_OPTIONS = listOf(
  MarketplaceTabSearchSortByOptions.RELEVANCE,
  MarketplaceTabSearchSortByOptions.DOWNLOADS,
  MarketplaceTabSearchSortByOptions.RATING,
  MarketplaceTabSearchSortByOptions.NAME,
  MarketplaceTabSearchSortByOptions.UPDATE_DATE,
)

private const val MAX_FILTER_VALUE_LENGTH = 80
private const val ELLIPSIS = "..."

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls

internal sealed interface PluginSectionId {
  data object Installing : PluginSectionId
  data object Installed : PluginSectionId
  data object Bundled : PluginSectionId
  data object Internal : PluginSectionId
  data object Suggested : PluginSectionId
  data object Marketplace : PluginSectionId
  data object CustomRepositoryCatalog : PluginSectionId

  data class CustomRepository(val repositoryId: String) : PluginSectionId {
    init {
      require(repositoryId.isNotBlank()) { "Custom repository ID must not be blank" }
    }
  }
}

internal data class PluginOccurrenceId(
  val sectionId: PluginSectionId,
  val pluginId: PluginId,
)

internal data class PluginItemState(
  val pluginId: PluginId,
  val name: @NlsSafe String?,
  val contentRevision: Long = 0,
  val modelHandle: PluginItemModelHandle? = null,
  val rowInput: PluginRowInput? = null,
  val searchCategory: @Nls String? = null,
  val searchVendor: String? = null,
  val searchTags: Set<String> = emptySet(),
) {
  init {
    require(contentRevision >= 0) { "Plugin content revision must not be negative" }
    require(modelHandle == null || modelHandle.pluginId == pluginId) {
      "Plugin model ID ${modelHandle?.pluginId} does not match item ID $pluginId"
    }
    require(rowInput == null || modelHandle != null) { "Plugin row input requires a model handle" }
  }
}

internal enum class BundledPluginCategoryAction {
  EnableAll,
  DisableAll,
}

internal data class BundledPluginCategoryGroupState(
  val category: @Nls String,
  val pluginIds: List<PluginId>,
  val action: BundledPluginCategoryAction,
)

internal fun bundledPluginCategory(category: @Nls String?): @Nls String {
  return category?.takeIf(String::isNotEmpty) ?: IdeBundle.message("plugins.configurable.other.bundled")
}

internal data class UnifiedPluginFilterOptions(
  val vendors: List<String> = emptyList(),
  val categories: List<String> = emptyList(),
  val tags: List<String> = emptyList(),
  val repositories: List<String> = emptyList(),
  val facetValuesLoading: Boolean = false,
  val repositoriesLoading: Boolean = false,
)

internal data class UnifiedPluginsSearchControlsState(
  val options: UnifiedPluginFilterOptions = UnifiedPluginFilterOptions(),
  val selectedVendors: Set<String> = emptySet(),
  val selectedCategories: Set<String> = emptySet(),
  val selectedTags: Set<String> = emptySet(),
  val selectedRepositories: Set<String> = emptySet(),
  val selectedInstalledFilter: UnifiedPluginInstalledFilter? = null,
  val effectiveSort: MarketplaceTabSearchSortByOptions = MarketplaceTabSearchSortByOptions.RELEVANCE,
  val sortVisible: Boolean = false,
) {
  val filterSelected: Boolean
    get() = selectedVendors.isNotEmpty() || selectedCategories.isNotEmpty() || selectedTags.isNotEmpty() ||
            selectedRepositories.isNotEmpty() || selectedInstalledFilter != null
}

/** Identity-only bridge to a mutable model. Mutations must be represented by [PluginItemState.contentRevision]. */
internal class PluginItemModelHandle(
  val model: PluginUiModel,
) {
  val pluginId: PluginId
    get() = model.pluginId

  override fun equals(other: Any?): Boolean {
    return this === other || other is PluginItemModelHandle && model === other.model
  }

  override fun hashCode(): Int = System.identityHashCode(model)

  override fun toString(): String = "PluginItemModelHandle($pluginId)"
}

internal data class PluginSectionError(
  val message: @Nls String,
  val retryable: Boolean,
)

internal sealed interface PluginSectionStatus {
  data object Ready : PluginSectionStatus

  data class Loading(val showingStaleContent: Boolean) : PluginSectionStatus

  data class Degraded(val error: PluginSectionError) : PluginSectionStatus

  data class Failed(val error: PluginSectionError) : PluginSectionStatus
}

internal data class PluginSectionState(
  val id: PluginSectionId,
  val title: @Nls String? = null,
  val items: List<PluginItemState> = emptyList(),
  val status: PluginSectionStatus = PluginSectionStatus.Ready,
  val expanded: Boolean = false,
  val categoryGroups: List<BundledPluginCategoryGroupState> = emptyList(),
  val collapsedItemLimit: Int = COLLAPSED_ITEM_LIMIT,
) {
  init {
    require(collapsedItemLimit > 0) { "The collapsed item limit must be positive" }
  }

  val count: Int?
    get() = if (status is PluginSectionStatus.Loading) null else items.size

  val canExpand: Boolean
    get() = items.size > collapsedItemLimit

  val displayItems: List<PluginItemState> = items.subList(0, minOf(items.size, MAX_DISPLAYED_ITEM_COUNT))

  val exceedsDisplayLimit: Boolean
    get() = items.size > MAX_DISPLAYED_ITEM_COUNT

  val visibleItems: List<PluginItemState> = displayItems.subList(
    0,
    if (expanded) displayItems.size else minOf(displayItems.size, collapsedItemLimit),
  )

  fun occurrenceId(pluginId: PluginId): PluginOccurrenceId = PluginOccurrenceId(id, pluginId)

  companion object {
    const val COLLAPSED_ITEM_LIMIT: Int = 3
    const val MAX_DISPLAYED_ITEM_COUNT: Int = 1_000
  }
}

internal data class PluginsQueryState(
  val rawQuery: String = "",
  val normalizedQuery: String = "",
  val revision: Long = 0,
  val scope: PluginsQueryScope = PluginsQueryScope.Unified,
)

internal enum class PluginsQueryScope {
  Unified,
  Installed,
  Marketplace,
}

internal data class UnifiedPluginsPageState(
  val query: PluginsQueryState,
  val sections: List<PluginSectionState>,
  val selectedOccurrences: List<PluginOccurrenceId>,
  val searchControls: UnifiedPluginsSearchControlsState = UnifiedPluginsSearchControlsState(),
) {
  val selectedOccurrence: PluginOccurrenceId?
    get() = selectedOccurrences.lastOrNull()
}

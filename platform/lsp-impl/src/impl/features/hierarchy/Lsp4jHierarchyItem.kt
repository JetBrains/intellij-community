package com.intellij.platform.lsp.impl.features.hierarchy

import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.SymbolTag
import org.eclipse.lsp4j.TypeHierarchyItem
import org.jetbrains.annotations.Nls

internal data class Lsp4jHierarchyItem(
  val name: @Nls String,
  val detail: @Nls String?,
  val kind: SymbolKind,
  val tags: List<SymbolTag>?,
  val uri: String,
  val range: Range,
  val selectionRange: Range,
  val data: Any?,
  val originalItem: Any,

  ) {
  internal fun toCallHierarchyItem(): CallHierarchyItem? = originalItem as? CallHierarchyItem

  internal fun toTypeHierarchyItem(): TypeHierarchyItem? = originalItem as? TypeHierarchyItem

  companion object {
    @JvmStatic
    internal fun from(item: CallHierarchyItem): Lsp4jHierarchyItem = Lsp4jHierarchyItem(
      name = item.name,
      detail = item.detail,
      kind = item.kind,
      tags = item.tags,
      uri = item.uri,
      range = item.range,
      selectionRange = item.selectionRange,
      data = item.data,
      originalItem = item,
    )

    @JvmStatic
    internal fun from(item: TypeHierarchyItem): Lsp4jHierarchyItem = Lsp4jHierarchyItem(
      name = item.name,
      detail = item.detail,
      kind = item.kind,
      tags = item.tags,
      uri = item.uri,
      range = item.range,
      selectionRange = item.selectionRange,
      data = item.data,
      originalItem = item,
    )
  }
}
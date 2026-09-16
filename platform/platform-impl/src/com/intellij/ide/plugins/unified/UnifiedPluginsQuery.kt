// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.SearchWords
import java.util.LinkedHashSet

internal enum class UnifiedPluginQueryAttribute(val prefix: String) {
  Vendor(SearchWords.VENDOR.value),
  Category("/category:"),
  Tag(SearchWords.TAG.value),
  Repository(SearchWords.REPOSITORY.value),
  Sort(SearchWords.SORT_BY.value),
}

internal enum class UnifiedPluginInstalledFilter(val command: String) {
  UpdateAvailable("/outdated"),
  Enabled("/enabled"),
  Disabled("/disabled"),
  Invalid("/invalid"),
  UpdatedBundled("/updatedBundled"),
}

internal enum class UnifiedPluginQueryCommand(val command: String) {
  UpdateAvailable(UnifiedPluginInstalledFilter.UpdateAvailable.command),
  Enabled(UnifiedPluginInstalledFilter.Enabled.command),
  Disabled(UnifiedPluginInstalledFilter.Disabled.command),
  Invalid(UnifiedPluginInstalledFilter.Invalid.command),
  UpdatedBundled(UnifiedPluginInstalledFilter.UpdatedBundled.command),
  Bundled("/bundled"),
  UserInstalled("/userInstalled"),
  Downloaded("/downloaded"),
  Suggested(SearchWords.SUGGESTED.value),
  StaffPicks(SearchWords.STAFF_PICKS.value),
  Internal(SearchWords.INTERNAL.value),
  ;

  val installedFilter: UnifiedPluginInstalledFilter?
    get() = UnifiedPluginInstalledFilter.entries.firstOrNull { it.command == command }
}

internal sealed interface UnifiedPluginSearchControlIntent {
  data class ToggleAttribute(
    val attribute: UnifiedPluginQueryAttribute,
    val value: String,
    val selected: Boolean,
  ) : UnifiedPluginSearchControlIntent

  data class ToggleInstalledFilter(
    val filter: UnifiedPluginInstalledFilter,
    val selected: Boolean,
  ) : UnifiedPluginSearchControlIntent

  data class SelectSort(val sort: MarketplaceTabSearchSortByOptions) : UnifiedPluginSearchControlIntent
}

internal sealed interface UnifiedPluginQueryPart {
  val rawText: String

  data class Attribute(
    val attribute: UnifiedPluginQueryAttribute,
    val value: String,
    override val rawText: String,
  ) : UnifiedPluginQueryPart

  data class Command(
    val command: UnifiedPluginQueryCommand,
    override val rawText: String,
  ) : UnifiedPluginQueryPart

  data class Other(override val rawText: String) : UnifiedPluginQueryPart
}

internal class UnifiedPluginsQuery private constructor(
  val rawQuery: String,
  internal val parts: List<UnifiedPluginQueryPart>,
) {
  val vendors: Set<String> = attributeValues(UnifiedPluginQueryAttribute.Vendor)
  val categories: Set<String> = attributeValues(UnifiedPluginQueryAttribute.Category)
  val tags: Set<String> = attributeValues(UnifiedPluginQueryAttribute.Tag)
  val repositories: Set<String> = attributeValues(UnifiedPluginQueryAttribute.Repository)
  val effectiveInstalledFilter: UnifiedPluginInstalledFilter? = parts.asReversed().firstNotNullOfOrNull { part ->
    (part as? UnifiedPluginQueryPart.Command)?.command?.installedFilter
  }
  private val commands: Set<UnifiedPluginQueryCommand> = parts.asSequence()
    .filterIsInstance<UnifiedPluginQueryPart.Command>()
    .map(UnifiedPluginQueryPart.Command::command)
    .toCollection(LinkedHashSet())

  val hasInstalledConstraint: Boolean
    get() = effectiveInstalledFilter != null || commands.any { it in INSTALLED_ONLY_COMMANDS }

  val requestsSuggested: Boolean
    get() = UnifiedPluginQueryCommand.Suggested in commands

  val requestsInternal: Boolean
    get() = UnifiedPluginQueryCommand.Internal in commands

  val hasPrimaryMarketplaceMode: Boolean
    get() = commands.any { it in PRIMARY_MARKETPLACE_COMMANDS }

  val effectiveSort: MarketplaceTabSearchSortByOptions = parts.asReversed().asSequence()
                                                           .filterIsInstance<UnifiedPluginQueryPart.Attribute>()
                                                           .firstOrNull { it.attribute == UnifiedPluginQueryAttribute.Sort }
                                                           ?.value
                                                           ?.let(MarketplaceTabSearchSortByOptions::getByQueryOrNull)
                                                         ?: MarketplaceTabSearchSortByOptions.RELEVANCE

  val hasPopupFilter: Boolean
    get() = vendors.isNotEmpty() || categories.isNotEmpty() || tags.isNotEmpty() ||
            repositories.isNotEmpty() || effectiveInstalledFilter != null

  fun withAttribute(attribute: UnifiedPluginQueryAttribute, value: String, selected: Boolean): String {
    require(attribute != UnifiedPluginQueryAttribute.Sort) { "Use withSort for sort attributes" }
    require(value.isNotBlank()) { "Plugin query attribute value must not be blank" }
    val hasValue = parts.any { part ->
      part is UnifiedPluginQueryPart.Attribute && part.attribute == attribute && part.value == value
    }
    if (selected == hasValue) return rawQuery
    return edit(
      remove = { part ->
        !selected && part is UnifiedPluginQueryPart.Attribute && part.attribute == attribute && part.value == value
      },
      addition = if (selected) attribute.prefix + SearchQueryParser.wrapAttribute(value) else null,
    )
  }

  fun withInstalledFilter(filter: UnifiedPluginInstalledFilter, selected: Boolean): String {
    val installedFilterParts = parts.asSequence()
      .filterIsInstance<UnifiedPluginQueryPart.Command>()
      .filter { it.command.installedFilter != null }
      .toList()
    val isCanonical = if (selected) {
      installedFilterParts.size == 1 && installedFilterParts.single().command.installedFilter == filter
    }
    else {
      installedFilterParts.isEmpty()
    }
    if (isCanonical) return rawQuery
    return edit(
      remove = { part ->
        part is UnifiedPluginQueryPart.Command && part.command.installedFilter != null
      },
      addition = if (selected) filter.command else null,
    )
  }

  fun withSort(sort: MarketplaceTabSearchSortByOptions): String {
    val explicitSortParts = parts.filterIsInstance<UnifiedPluginQueryPart.Attribute>()
      .filter { it.attribute == UnifiedPluginQueryAttribute.Sort }
    val hasCanonicalSort = if (sort == MarketplaceTabSearchSortByOptions.RELEVANCE) {
      explicitSortParts.none { MarketplaceTabSearchSortByOptions.getByQueryOrNull(it.value) != null }
    }
    else {
      explicitSortParts.size == 1 && explicitSortParts.single().value == sort.query
    }
    if (hasCanonicalSort) return rawQuery

    return edit(
      remove = { part ->
        part is UnifiedPluginQueryPart.Attribute &&
        part.attribute == UnifiedPluginQueryAttribute.Sort &&
        MarketplaceTabSearchSortByOptions.getByQueryOrNull(part.value) != null
      },
      addition = if (sort == MarketplaceTabSearchSortByOptions.RELEVANCE) null else SearchWords.SORT_BY.value + sort.query,
    )
  }

  internal fun renderExcluding(exclude: (UnifiedPluginQueryPart) -> Boolean): String {
    return parts.asSequence().filterNot(exclude).joinToString(" ", transform = UnifiedPluginQueryPart::rawText).trim()
  }

  private fun attributeValues(attribute: UnifiedPluginQueryAttribute): Set<String> {
    return parts.asSequence()
      .filterIsInstance<UnifiedPluginQueryPart.Attribute>()
      .filter { it.attribute == attribute }
      .map(UnifiedPluginQueryPart.Attribute::value)
      .filter(String::isNotEmpty)
      .toCollection(LinkedHashSet())
  }

  private fun edit(remove: (UnifiedPluginQueryPart) -> Boolean, addition: String?): String {
    return buildList {
      parts.asSequence().filterNot(remove).mapTo(this, UnifiedPluginQueryPart::rawText)
      if (addition != null) add(addition)
    }.joinToString(" ").trim()
  }

  companion object {
    private val INSTALLED_ONLY_COMMANDS = setOf(
      UnifiedPluginQueryCommand.Bundled,
      UnifiedPluginQueryCommand.UserInstalled,
      UnifiedPluginQueryCommand.Downloaded,
    )
    private val PRIMARY_MARKETPLACE_COMMANDS = setOf(
      UnifiedPluginQueryCommand.Suggested,
      UnifiedPluginQueryCommand.StaffPicks,
      UnifiedPluginQueryCommand.Internal,
    )

    fun parse(rawQuery: String): UnifiedPluginsQuery {
      val atoms = tokenize(rawQuery)
      val parts = ArrayList<UnifiedPluginQueryPart>(atoms.size)
      val iterator = atoms.listIterator()
      while (iterator.hasNext()) {
        val atom = iterator.next()
        val attribute = UnifiedPluginQueryAttribute.entries.firstOrNull { atom.rawText.startsWith(it.prefix) }
        if (attribute != null) {
          val suffix = atom.rawText.substring(attribute.prefix.length)
          val valueAtom = if (suffix.isEmpty() && iterator.hasNext()) iterator.next() else null
          val encodedValue = suffix.ifEmpty { valueAtom?.rawText.orEmpty() }
          if (encodedValue.isNotEmpty()) {
            val end = valueAtom?.end ?: atom.end
            parts.add(
              UnifiedPluginQueryPart.Attribute(
                attribute = attribute,
                value = unwrapAttribute(encodedValue).trim(),
                rawText = rawQuery.substring(atom.start, end),
              )
            )
            continue
          }
        }

        val command = UnifiedPluginQueryCommand.entries.firstOrNull { it.command == atom.rawText }
        parts.add(
          if (command == null) UnifiedPluginQueryPart.Other(atom.rawText)
          else UnifiedPluginQueryPart.Command(command, atom.rawText)
        )
      }
      return UnifiedPluginsQuery(rawQuery, parts)
    }

    private fun tokenize(query: String): List<QueryAtom> {
      val result = ArrayList<QueryAtom>()
      appendAtoms(query, 0, result)
      return result
    }

    private tailrec fun appendAtoms(query: String, offset: Int, result: MutableList<QueryAtom>) {
      val start = skipWhitespace(query, offset)
      if (start == query.length) return
      val end = findAtomEnd(query, start, quoted = false)
      result.add(QueryAtom(start, end, query.substring(start, end)))
      appendAtoms(query, end, result)
    }

    private tailrec fun skipWhitespace(query: String, offset: Int): Int {
      return if (offset == query.length || !query[offset].isWhitespace()) offset else skipWhitespace(query, offset + 1)
    }

    private tailrec fun findAtomEnd(query: String, offset: Int, quoted: Boolean): Int {
      if (offset == query.length) return offset
      val character = query[offset]
      if (!quoted && character.isWhitespace()) return offset
      return findAtomEnd(query, offset + 1, if (character == '"') !quoted else quoted)
    }

    private fun unwrapAttribute(value: String): String {
      return if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
        value.substring(1, value.lastIndex)
      }
      else {
        value
      }
    }
  }
}

private data class QueryAtom(
  val start: Int,
  val end: Int,
  val rawText: String,
)

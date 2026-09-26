package com.intellij.platform.lsp.impl.features.completion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Sorts LSP-based completion items by
 * [CompletionItem.sortText](https://microsoft.github.io/language-server-protocol/specification/#completionItem)
 *
 * ### Test coverage
 * `com.intellij.tailwind.lsp.TailwindLspCompletionTest.testTypingInHtmlNotInPackage`
 *
 * ### Implementation details
 * Another way to apply LSP-based sorting is to use the sorter right in the [LspCompletionContributor] could be like this:
 * ```kotlin
 * val weigher = object : LookupElementWeigher("lspSorter", / * negated = * / false, / * dependsOnPrefix = * / false) {
 *   override fun weigh(element: LookupElement): Comparable<String> =
 *     (element.`object` as? LspCompletionObject)?.completionItem?.sortText
 *     ?: ""
 * }
 * val sorter = CompletionSorter.defaultSorter(parameters, result.prefixMatcher).weighAfter("priority", weigher)
 * val sortingResultSet = result.withRelevanceSorter(sorter)
 * processCompletionItemsImpl(server, document, offset, sortingResultSet, completionList.items) {...}
 * ```
 * but that doesn't work, unfortunately.
 * That way, the PrioritizedLookupElement returned from LspCompletionSupport.createLookupElement
 * doesn't help to bubble up the items in the completion list.
 */
internal class LspCompletionWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<ReverseComparableString>? {
    val sortText = (element.`object` as? LspCompletionObject)?.completionItem?.sortText
                   ?: return null
    return ReverseComparableString(sortText)
  }
}

internal class ReverseComparableString(val string: String) : Comparable<ReverseComparableString> {
  override fun compareTo(other: ReverseComparableString): Int = other.string.compareTo(string)
  override fun toString(): String = string
}

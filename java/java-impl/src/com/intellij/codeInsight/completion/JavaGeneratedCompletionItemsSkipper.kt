package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement

/**
 * Is somewhat artificial filter. It is required for Fleet.
 * Some completion items have to be filtered out only for Fleet without doing some shady stuff or exposing internals to the public API.
 * It is better to invoke this PreselectionSkip machinery (even though it's not designed for filtering), than to make another
 * CompletionContributor that will filter the CompletionResultSet. 
 */
public class JavaGeneratedCompletionItemsSkipper : CompletionPreselectSkipper() {
  override fun skipElement(element: LookupElement, location: CompletionLocation): Boolean {
    val triggersWizzard = element.getUserData(JavaGenerateMemberCompletionContributor.GENERATE_ELEMENT)
    return triggersWizzard == true
  }
}

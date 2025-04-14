// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.commands

import com.intellij.codeInsight.completion.command.combineFragments
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult
import org.junit.Test
import kotlin.test.assertEquals

class CommandDocumentationProviderFragmentTest {

  @Test
  fun testCombineFragmentsBetween() {
    val fragments = listOf(
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.ADDED, 1, 3),
    )
    val additionalIndexes = listOf(2)

    val combinedResult = combineFragments(fragments, additionalIndexes)

    val expectedCombinedResult = listOf(
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.ADDED, 1, 2),
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.ADDED, 2, 3),
    )

    assertEquals(expectedCombinedResult, combinedResult)
  }

  @Test
  fun testCombineFragmentsBeforeAfter() {
    val fragments = listOf(
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.ADDED, 4, 6),
    )
    val additionalIndexes = listOf(2, 7)

    val combinedResult = combineFragments(fragments, additionalIndexes)

    val expectedCombinedResult = listOf(
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.UPDATED, 2, 2),
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.ADDED, 4, 6),
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.UPDATED, 7, 7),
    )

    assertEquals(expectedCombinedResult, combinedResult)
  }


  @Test
  fun testCommonCombineFragments() {
    val fragments = listOf(
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.ADDED, 1, 3),
      IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.DELETED, 5, 7))

    val additionalIndexes = listOf(4, 6)

    val combinedResult = combineFragments(fragments, additionalIndexes)

    val expectedCombinedResult = listOf(IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.ADDED, 1, 3), IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.UPDATED, 4, 4), IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.DELETED, 5, 6), IntentionPreviewDiffResult.Fragment(IntentionPreviewDiffResult.HighlightingType.DELETED, 6, 7))

    assertEquals(expectedCombinedResult, combinedResult)
  }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions.lists

import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Describe the structure of split / join operations for list elements.
 * <code>
 *  test(p1, p2, p3)
 * </code>
 * and
 * <code>
 *  test(p1,
 *       p2,
 *       p3)
 * </code>
 * The actions provide operations to convert form (1) to (2) and back
 *
 * @see DefaultListSplitJoinContext default implementation
 */
@ApiStatus.Experimental
interface ListSplitJoinContext {

  companion object {
    val EXTENSION: LanguageExtension<ListSplitJoinContext> = LanguageExtension<ListSplitJoinContext>("com.intellij.listSplitJoinContext")
  }

  /**
   * Checks if the split operation is available in the context
   */
  fun isSplitAvailable(data: ListWithElements): Boolean

  /**
   * Checks if the join operation is available in the context
   */
  fun isJoinAvailable(data: ListWithElements): Boolean

  /**
   * Extracts list and elements for the operations. Null means that the operation isn't available
   */
  fun extractData(context: PsiElement): ListWithElements?

  /**
   * Text for the split operation, to show in the editor
   */
  @IntentionName
  fun getSplitText(data: ListWithElements): String

  /**
   * Text for the join operation, to show in the editor
   */
  @IntentionName
  fun getJoinText(data: ListWithElements): String

  /**
   * Reformat the range after processing. It is delegated to context, because based on the psi structure it can be optional
   */
  fun reformatRange(file: PsiFile, rangeToAdjust: TextRange, split: JoinOrSplit)

  /**
   * Collects the ordered list (asc) of replacements to update document for the splitting
   */
  fun getReplacementsForSplitting(data: ListWithElements): List<Pair<TextRange, String>>

  /**
   * Collects the ordered list (asc) of replacements to update document for the joining
   */
  fun getReplacementsForJoining(data: ListWithElements): List<Pair<TextRange, String>>
}

class ListWithElements(val list: PsiElement, val elements: List<PsiElement>)
enum class JoinOrSplit { JOIN, SPLIT }

internal fun getListSplitJoinContext(element: PsiElement, joinOrSplit: JoinOrSplit): Pair<ListSplitJoinContext, ListWithElements>? {
  val language = element.language
  val extensions = ListSplitJoinContext.EXTENSION.allForLanguage(language)
  for (extension in extensions) {
    val data = extension.extractData(element) ?: continue
    if (joinOrSplit == JoinOrSplit.SPLIT && extension.isSplitAvailable(data) ||
        joinOrSplit == JoinOrSplit.JOIN && extension.isJoinAvailable(data)) {
      return extension to data
    }
  }
  return null
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions.lists

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafElement

/**
 * Default implementation of split/join operations
 * @see CommaListSplitJoinContext
 */
abstract class DefaultListSplitJoinContext : ListSplitJoinContext {

  /**
   * Returns true, if the element is a separator of the list (e.g. comma).
   * If there are no explicit separators, it should be "false"
   */
  abstract fun isSeparator(element: PsiElement): Boolean

  /**
   * Extracts list and elements for the operations. Null means that the operation isn't available
   */
  abstract override fun extractData(context: PsiElement): ListWithElements?

  /**
   * Returns true if the element is an acceptable part of the list
   * e.g. spread element if it is a top level element in the list, custom comments and so on. Shouldn't include separators
   */
  open fun isValidIntermediateElement(data: ListWithElements, element: PsiElement): Boolean = element is PsiWhiteSpace

  /**
   * Checks if we need to insert (to keep) new line at the start of list:
   * <code>
   * foo(a,
   *     b)
   * </code>
   * vs
   * <code>
   * foo(
   *     a,
   *     b)
   * </code>
   */
  open fun needHeadBreak(data: ListWithElements, firstElement: PsiElement, mode: JoinOrSplit): Boolean = false

  /**
   * Checks if we need to insert (to keep) new line at the end of list:
   * <code>
   * foo(a,
   *     b)
   * </code>
   * vs
   * <code>
   * foo(a,
   *     b
   * )
   * </code>
   */
  open fun needTailBreak(data: ListWithElements, lastElement: PsiElement, mode: JoinOrSplit): Boolean = false

  /**
   * Tries to get the nearest prev line break (usually PsiWhiteSpace), should skip separators and non-meaning elements (comments)
   */
  open fun prevBreak(data: ListWithElements, element: PsiElement): PsiElement? {
    return nextPrevBreak(data, element, false)
  }

  /**
   * Tries to get the nearest next line break (usually PsiWhiteSpace), should skip separators and non-meaning elements (comments)
   */
  open fun nextBreak(data: ListWithElements, element: PsiElement): PsiElement? {
    return nextPrevBreak(data, element, true)
  }

  override fun getSplitText(data: ListWithElements): String = CodeInsightBundle.message("intention.family.name.split.values")

  override fun getJoinText(data: ListWithElements): String = CodeInsightBundle.message("intention.family.name.join.values")

  override fun isSplitAvailable(data: ListWithElements): Boolean =
    data.elements.size > 1 && validateRange(data) && getReplacementsForSplitting(data).isNotEmpty()

  override fun isJoinAvailable(data: ListWithElements): Boolean =
    data.elements.size > 1 && validateRange(data) && getReplacementsForJoining(data).isNotEmpty()

  override fun reformatRange(file: PsiFile, rangeToAdjust: TextRange, split: JoinOrSplit): Unit =
    CodeStyleManager.getInstance(file.project).adjustLineIndent(file, rangeToAdjust)

  override fun getReplacementsForSplitting(data: ListWithElements): List<Pair<TextRange, String>> {
    val elements = data.elements

    val replacements = mutableListOf<Pair<TextRange, String>>()
    val firstElement = elements.first()
    val lastElement = elements.last()

    addHeadReplacementsForSplitting(data, replacements, firstElement)

    var hasInnerReplacement = false
    for (el in elements.dropLast(1)) {
      if (nextBreak(data, el) != null) continue
      val offset = findOffsetForBreakAfter(data, el)
      val textRange = TextRange(offset, offset)
      replacements.add(textRange to "\n")
      hasInnerReplacement = true
    }

    //don't split if there are no nested elements for splitting
    if (!hasInnerReplacement) return emptyList()

    addTailReplacementsForSplitting(data, replacements, lastElement)

    return replacements
  }

  override fun getReplacementsForJoining(data: ListWithElements): List<Pair<TextRange, String>> {
    val elements = data.elements

    val replacements = mutableListOf<Pair<TextRange, String>>()
    val firstElement = elements.first()
    val lastElement = elements.last()

    addHeadReplacementsForJoining(data, replacements, firstElement)

    for (current in elements.dropLast(1)) {
      val nextBreak = nextBreak(data, current) ?: continue
      replacements.add(nextBreak.textRange to " ")
      addSiblingWhitespaceReplacement(replacements, nextBreak)
    }

    addTailReplacementsForJoining(data, replacements, lastElement)

    return replacements
  }

  protected open fun addHeadReplacementsForSplitting(data: ListWithElements,
                                                     replacements: MutableList<Pair<TextRange, String>>,
                                                     firstElement: PsiElement) {
    if (needHeadBreak(data, firstElement, JoinOrSplit.SPLIT) && prevBreak(data, firstElement) == null) {
      val offset = firstElement.textRange.startOffset
      replacements.add(TextRange(offset, offset) to "\n")
    }
  }

  protected open fun addHeadReplacementsForJoining(data: ListWithElements,
                                                   replacements: MutableList<Pair<TextRange, String>>,
                                                   firstElement: PsiElement) {
    if (!needHeadBreak(data, firstElement, JoinOrSplit.JOIN)) {
      val prevBreak = prevBreak(data, firstElement)
      if (prevBreak != null) {
        replacements.add(prevBreak.textRange to getHeadBreakJoinReplacement(firstElement))
        addSiblingWhitespaceReplacement(replacements, prevBreak)
      }
    }
  }

  protected open fun addTailReplacementsForSplitting(data: ListWithElements,
                                                     replacements: MutableList<Pair<TextRange, String>>,
                                                     lastElement: PsiElement) {
    if (nextBreak(data, lastElement) == null && needTailBreak(data, lastElement, JoinOrSplit.SPLIT)) {
      val offset = findOffsetForBreakAfter(data, lastElement)
      replacements.add(TextRange(offset, offset) to "\n")
    }
  }

  protected open fun addTailReplacementsForJoining(data: ListWithElements,
                                                   replacements: MutableList<Pair<TextRange, String>>,
                                                   lastElement: PsiElement) {
    if (!needTailBreak(data, lastElement, JoinOrSplit.JOIN)) {
      val nextBreak = nextBreak(data, lastElement)
      if (nextBreak != null) {
        replacements.add(nextBreak.textRange to getTailBreakJoinReplacement(lastElement))
        addSiblingWhitespaceReplacement(replacements, nextBreak)
      }
    }
  }

  protected open fun getTailBreakJoinReplacement(lastElement: PsiElement): String = ""
  protected open fun getHeadBreakJoinReplacement(firstElement: PsiElement): String = ""

  protected fun findOffsetForBreakAfter(data: ListWithElements, element: PsiElement): Int {
    val after = skipAcceptableElements(data, element)
    if (isSeparator(after)) {
      //accept comments after "," as a part of the break
      return separatorOrNewLineComment(data, after).textRange.endOffset
    }

    return after.textRange.endOffset
  }

  protected fun hasSeparatorAfter(data: ListWithElements,
                                  lastElement: PsiElement): Boolean {
    val afterWhitespaces = skipAcceptableElements(data, lastElement)
    return isSeparator(afterWhitespaces)
  }

  protected fun skipAcceptableElements(data: ListWithElements, element: PsiElement): PsiElement {
    var curr = element
    while (true) {
      val nextCandidate = curr.nextSibling
      if (nextCandidate == null || !isValidIntermediateElement(data, nextCandidate)) break
      curr = nextCandidate
    }
    val next = curr.nextSibling

    return if (next != null && isSeparator(next)) next else curr
  }

  private fun addSiblingWhitespaceReplacement(replacements: MutableList<Pair<TextRange, String>>, lineBreak: PsiElement) {
    val nextSibling = lineBreak.nextSibling
    if (nextSibling is PsiWhiteSpace) {
      //in some languages (GO) new lines and indents are not merged, so we need to remove it manually
      replacements.add(nextSibling.textRange to "")
    }
  }

  private fun nextPrevBreak(data: ListWithElements, element: PsiElement, next: Boolean): PsiElement? {
    var current = if (next) element.nextSibling else element.prevSibling
    while (current != null && (isValidIntermediateElement(data, current) || isSeparator(current))) {
      if (current is PsiWhiteSpace && current.textContains('\n')) return current
      current = if (next) current.nextSibling else current.prevSibling
    }
    return null
  }

  private fun validateRange(data: ListWithElements): Boolean {
    val elements = data.elements.toSet()
    return extractRange(data).all { elements.contains(it) || isSeparator(it) || isValidIntermediateElement(data, it) } &&
           validateHeadElement(data, data.elements.first()) &&
           validateTailElement(data, data.elements.last())
  }

  protected open fun validateHeadElement(data: ListWithElements, head: PsiElement): Boolean =
    validateHeadOrTail(data, head, false)

  protected open fun validateTailElement(data: ListWithElements, tail: PsiElement): Boolean =
    validateHeadOrTail(data, tail, true)

  /**
   * Reject split/join if before head / after tail there is an unsupported comment 
   */
  private fun validateHeadOrTail(data: ListWithElements, element: PsiElement, next: Boolean): Boolean {
    var current = when (next) {
      true -> element.nextSibling
      false -> element.prevSibling
    }
    while (current != null) {
      if (current is PsiComment) {
        if (!isValidIntermediateElement(data, current)) return false
      }
      else if (current !is PsiWhiteSpace) {
        break
      }
      
      current = when (next) {
        true -> current.nextSibling
        false -> current.prevSibling
      }
    }

    return true
  }


  private fun extractRange(data: ListWithElements): List<PsiElement> {
    val mutableList = mutableListOf<PsiElement>()
    var collectStart = false
    val startElement = data.elements.first()
    val endElement = data.elements.last()

    var curr = data.list.firstChild
    while (curr != null) {
      if (!collectStart) {
        if (startElement == curr) {
          collectStart = true
        }
      }
      else {
        mutableList.add(curr)

        if (curr == endElement) break
      }
      curr = curr.nextSibling
    }
    return mutableList
  }

  /**
   * Handle a specific case when a comment is started at the end of the parameter after comma and have several lines.
   * In this case inserting the line break After the comment is more preferable to keep the parameters aligned
   */
  private fun separatorOrNewLineComment(data: ListWithElements,
                                        separator: PsiElement): PsiElement {
    var next = separator
    while (true) {
      val candidate = next.nextSibling
      if (candidate is PsiWhiteSpace) {
        if (candidate.textContains('\n')) return separator
        next = candidate
        continue
      }
      else if (candidate is PsiComment && isValidIntermediateElement(data, candidate) && candidate.textContains('\n')) {
        return candidate
      }
      return separator
    }
  }
}

enum class TrailingComma { ADD_IF_NOT_EXISTS, REMOVE_IF_EXISTS, IGNORE }

abstract class CommaListSplitJoinContext : DefaultListSplitJoinContext() {
  override fun isSeparator(element: PsiElement): Boolean = isComma(element)

  override fun getSplitText(data: ListWithElements): String = CodeInsightBundle.message("intention.name.split.comma.values")
  override fun getJoinText(data: ListWithElements): String = CodeInsightBundle.message("intention.name.join.comma.values")

  private fun addOrRemoveTrailingComma(data: ListWithElements,
                                       replacements: MutableList<Pair<TextRange, String>>,
                                       split: JoinOrSplit,
                                       lastElement: PsiElement) {
    val comma = getTrailingComma(data, split, lastElement)

    if (comma == TrailingComma.ADD_IF_NOT_EXISTS) {
      if (!hasSeparatorAfter(data, lastElement)) {
        val offset = findOffsetForBreakAfter(data, lastElement)
        replacements.add(TextRange(offset, offset) to ",")
      }
    }
    else if (comma == TrailingComma.REMOVE_IF_EXISTS) {
      val endElement = skipAcceptableElements(data, lastElement)
      if (isSeparator(endElement)) {
        replacements.add(endElement.textRange to "")
      }
    }
  }

  override fun addTailReplacementsForSplitting(data: ListWithElements,
                                               replacements: MutableList<Pair<TextRange, String>>,
                                               lastElement: PsiElement) {
    addOrRemoveTrailingComma(data, replacements, JoinOrSplit.SPLIT, lastElement)
    super.addTailReplacementsForSplitting(data, replacements, lastElement)
  }

  override fun addTailReplacementsForJoining(data: ListWithElements,
                                             replacements: MutableList<Pair<TextRange, String>>,
                                             lastElement: PsiElement) {
    addOrRemoveTrailingComma(data, replacements, JoinOrSplit.JOIN, lastElement)
    super.addTailReplacementsForJoining(data, replacements, lastElement)
  }

  protected open fun getTrailingComma(data: ListWithElements, mode: JoinOrSplit, lastElement: PsiElement): TrailingComma {
    return TrailingComma.IGNORE
  }
}

internal fun isComma(element: PsiElement?): Boolean = element is LeafElement && element.textMatches(",")
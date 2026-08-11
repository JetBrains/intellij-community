// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.elements

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.codeInsight.inline.completion.render.InlineCompletionTextRenderManager
import com.intellij.codeInsight.inline.completion.render.RenderedInlineCompletionElementDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle

private val logger = logger<InlineCompletionFragmentedElement>()

/**
 * Inline completion element rendered as a sequence of discontiguous text pieces.
 *
 * Useful when the suggestion is interleaved with characters that already exist in the editor —
 * for example, when an AI suggestion is shown on top of a lookup item: characters of the lookup
 * item that the user hasn't typed yet are rendered as "gap" fragments, and the AI continuation
 * after the lookup item is rendered as a trailing fragment.
 *
 * **Fragments are purely visual.** They control what gray text is displayed and where, but they
 * do not determine what gets inserted into the document. Fragments may include characters that
 * already exist in the editor (gap fills for the lookup item) — inserting those would duplicate text.
 *
 * Fragments are supplied in left-to-right order; [Presentable] aggregates
 * their bounds and offsets in iteration order. Overlapping or out-of-order fragments are
 * not supported. Empty [fragments] is allowed but produces an element with no visible text.
 *
 * [text] (used during insertion) returns [suggestionText] — the AI continuation that should be
 * inserted at the caret position. It intentionally excludes gap fragments rendered before the caret
 * so that [com.intellij.codeInsight.inline.completion.session.InlineCompletionContext.textToInsert]
 * produces the correct insertion payload.
 *
 * [suggestionText] carries the canonical text — typically the AI continuation after the full
 * lookup item — so the element can be rebuilt against a different prefix without re-querying the model.
 */
@ApiStatus.Internal
class InlineCompletionFragmentedElement(
  // Insertable AI continuation (what goes into the document on accept).
  // Not the same as the rendered text — fragments include gap fills that already exist in the editor.
  val suggestionText: String,
  val fragments: List<Fragment>,
) : InlineCompletionElement {

  /**
   * One piece of [InlineCompletionFragmentedElement].
   *
   * @param text gray text to display for this fragment.
   * @param relativeOffset distance in characters from the caret (the base render offset) to the
   *   start of this fragment, measured **leftward**. Computed as `caretOffset - fragmentStartOffset`.
   *
   *   - `0` — render at the caret (e.g. the trailing lookup-item tail + continuation).
   *   - `N > 0` — render N characters before the caret (e.g. a gap between typed hump characters).
   *
   *   Example: caret at offset 10, fragment should start at offset 7 → `relativeOffset = 3`.
   *
   *   Negative values are not supported.
   */
  data class Fragment(
    val text: String,
    val relativeOffset: Int,
  ) {
    init {
      if (relativeOffset < 0) {
        logger.error("Fragment.relativeOffset must not be negative, got $relativeOffset", RuntimeException())
      }
    }
  }

  override val text: String = suggestionText

  override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this)

  open class Presentable(
    override val element: InlineCompletionFragmentedElement,
  ) : InlineCompletionElement.Presentable {

    private val renderedDescriptors = mutableListOf<RenderedInlineCompletionElementDescriptor>()

    private var baseRenderOffset: Int = -1

    override fun isVisible(): Boolean = renderedDescriptors.isNotEmpty()

    override fun render(editor: Editor, offset: Int) {
      renderedDescriptors.clear()
      baseRenderOffset = offset
      val docLength = editor.document.textLength
      for ((text, relativeOffset) in element.fragments) {
        if (relativeOffset < 0) continue
        val fragmentOffset = offset - relativeOffset
        if (fragmentOffset !in 0..docLength) continue
        val descriptor = InlineCompletionTextRenderManager.render(
          editor = editor,
          text = text,
          attributes = InlineCompletionFontUtils.attributes(editor),
          offset = fragmentOffset,
          disposable = this
        )
        renderedDescriptors += descriptor
      }
    }

    override fun getBounds(): Rectangle? {
      return renderedDescriptors.mapNotNull { it.getRectangle() }
        .reduceOrNull { acc, rect -> acc.union(rect) }
    }

    override fun startOffset(): Int? =
      if (baseRenderOffset >= 0) baseRenderOffset
      else null

    override fun endOffset(): Int? =
      if (baseRenderOffset >= 0) baseRenderOffset
      else null

    override fun dispose() {
      renderedDescriptors.clear()
      baseRenderOffset = -1
    }
  }
}

/**
 * Manipulates [InlineCompletionFragmentedElement] when the platform takes a substring of the suggestion
 * (e.g. while the suggestion is highlighted or partially accepted). Registered with `order="first"` so it
 * wins over the built-in manipulators for fragmented elements.
 */
@ApiStatus.Internal
class InlineCompletionFragmentedElementManipulator : InlineCompletionElementManipulator {
  override fun isApplicable(element: InlineCompletionElement): Boolean {
    return element is InlineCompletionFragmentedElement
  }

  override fun substring(element: InlineCompletionElement, startOffset: Int, endOffset: Int): InlineCompletionElement? {
    element as InlineCompletionFragmentedElement
    if (startOffset == 0) {
      // Left/prefix slice [0, endOffset): rendered first, at the caret, so it must keep the before-caret gap
      // fragments and the lookup-item tail. substringFromStart trims only from the right, preserving them — this
      // is the case the highlighting split (and any [0, k) split) relies on.
      return element.substringFromStart(endOffset)
    }
    if (startOffset >= endOffset) return null
    // Right/suffix slice [startOffset, endOffset) of suggestionText, which is a suffix of the trailing fragment
    // (lookupItemTail + suggestionText), so this slice lives strictly inside the trailing fragment, after the tail.
    // It is produced only as the tail of a split (highlighting, splitByLength, insertSkipElementsAt,
    // onPairedEnclosureInsertion) or as the remainder after a consumed prefix (partial accept / truncateFirstSymbol).
    // In the split cases the matching [0, startOffset) part is emitted first and already carries the gap fragments
    // and the tail; in the prefix cases those characters are now real document text. A fragmented element does not
    // advance the render offset (startOffset() == endOffset() == baseRenderOffset), so keeping the gaps/tail here
    // would paint them a second time. We therefore collapse to a single caret-anchored fragment.
    val text = element.suggestionText.substring(startOffset, endOffset)
    return InlineCompletionFragmentedElement(
      suggestionText = text,
      fragments = listOf(InlineCompletionFragmentedElement.Fragment(text, 0)),
    )
  }

  private fun InlineCompletionFragmentedElement.substringFromStart(endOffset: Int): InlineCompletionFragmentedElement? {
    val text = suggestionText.substring(0, endOffset)
    val fragments = fragments.dropLastCharacters(suggestionText.length - endOffset)
    if (text.isEmpty() && fragments.isEmpty()) return null
    return InlineCompletionFragmentedElement(text, fragments)
  }

  private fun List<InlineCompletionFragmentedElement.Fragment>.dropLastCharacters(count: Int): List<InlineCompletionFragmentedElement.Fragment> {
    var toDrop = count
    return asReversed().mapNotNull { fragment ->
      if (toDrop >= fragment.text.length) {
        toDrop -= fragment.text.length
        null
      }
      else {
        val text = fragment.text.dropLast(toDrop)
        toDrop = 0
        fragment.copy(text = text)
      }
    }.asReversed()
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.util.SlowOperations
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.event.MouseEvent
import kotlin.math.abs

/**
 * A hyperlink that can be added to an editor.
 */
@ApiStatus.Internal
data class Hyperlink(
  /**
   * The hyperlink ID, unique within a given editor.
   */
  val id: HyperlinkId,
  /**
   * The document offset of the start of the hyperlink region.
   */
  val start: Int,
  /**
   * The document offset of the end of the hyperlink region.
   */
  val end: Int,
  /**
   * The text attributes of a link that's not followed or hovered.
   *
   * If `null`, the editor will fall back to the default hyperlink attributes if [action] is not `null` or no attributes otherwise.
   */
  val attributes: TextAttributes?,
  /**
   * The text attributes of a link that was followed (clicked).
   *
   * If `null`, the editor will fall back to the default followed hyperlink attributes.
   */
  val followedAttributes: TextAttributes?,
  /**
   * The text attributes of a link that is currently hovered.
   *
   * If `null`, the attributes won't change on hover.
   */
  val hoveredAttributes: TextAttributes?,
  /**
   * The highlighting layer.
   *
   * Normally just [HighlighterLayer.HYPERLINK], but a different value can be provided by an external source.
   */
  val layer: Int,
  /**
   * The action to execute on click.
   *
   * If `null`, then no action will be executed.
   */
  val action: ((EditorMouseEvent) -> Unit)?,
)

/**
 * A hyperlink ID.
 */
@ApiStatus.Internal
data class HyperlinkId(private val value: Long) {
  override fun toString(): String = value.toString()
}

/**
 * A helper class to add hyperlinks to an editor.
 *
 * Hyperlinks themselves are supposed to be provided by the client as [Hyperlink] instances.
 * The client is also responsible for assigning unique IDs to hyperlinks.
 * Hyperlinks are added using [addHyperlink] and removed using [removeHyperlink].
 */
@ApiStatus.Internal
class EditorHyperlinkApplier(private val editor: EditorEx, parentDisposable: Disposable) {
  private val highlightersById = hashMapOf<HyperlinkId, RangeHighlighterEx>()
  private val effectSupport = EditorHyperlinkEffectSupport(editor, MyEffectSupplier())

  init {
    editor.addEditorMouseListener(MyMouseListener(), parentDisposable)
    editor.addEditorMouseMotionListener(MyMouseMotionListener(), parentDisposable)
  }

  fun addHyperlink(hyperlink: Hyperlink) {
    require(hyperlink.id !in highlightersById) { "There's already a hyperlink with the id ${hyperlink.id}" }
    editor.markupModel.addRangeHighlighterAndChangeAttributes(
      if (hyperlink.action != null) CodeInsightColors.HYPERLINK_ATTRIBUTES else null,
      hyperlink.start,
      hyperlink.end,
      hyperlink.layer,
      HighlighterTargetArea.EXACT_RANGE,
      false,
    ) { highlighter ->
      if (hyperlink.attributes != null) {
        highlighter.textAttributes = hyperlink.attributes
      }
      highlighter.putUserData(HYPERLINK, hyperlink)
      highlightersById[hyperlink.id] = highlighter
    }
  }

  fun removeHyperlink(hyperlinkId: HyperlinkId) {
    val highlighter = highlightersById.remove(hyperlinkId)
    checkNotNull(highlighter) { "Hyperlink ${hyperlinkId} not found" }
    editor.markupModel.removeHighlighter(highlighter)
  }

  private fun findHyperlink(event: EditorMouseEvent): HighlightedLink? {
    if (event.area != EditorMouseEventArea.EDITING_AREA || !event.isOverText) return null
    return findHyperlink(event.offset)
  }

  private fun findHyperlink(offset: Int): HighlightedLink? {
    var result: HighlightedLink? = null
    editor.markupModel.processRangeHighlightersOverlappingWith(
      offset,
      offset
    ) { highlighter ->
      val hyperlink = highlighter.getHyperlinkOrNull()
      if (
        highlighter.isValid &&
        hyperlink != null &&
        offset in highlighter.startOffset until highlighter.endOffset // process...() treats the end as inclusive, that's why
      ) {
        result = HighlightedLink(hyperlink, highlighter)
        false
      }
      else {
        true
      }
    }
    return result
  }

  private inner class MyMouseListener : EditorMouseListener {
    private var mousePressedEvent: EditorMouseEvent? = null

    override fun mousePressed(event: EditorMouseEvent) {
      mousePressedEvent = event
    }

    override fun mouseReleased(event: EditorMouseEvent) {
      val mousePressedEvent = this.mousePressedEvent?.mouseEvent
      this.mousePressedEvent = null
      val mouseReleasedEvent = event.mouseEvent
      val sensitivity = EditorImpl.dragSensitivity()
      if (
        mouseReleasedEvent.button == MouseEvent.BUTTON1 &&
        !mouseReleasedEvent.isPopupTrigger &&
        mousePressedEvent != null &&
        mousePressedEvent.component == mouseReleasedEvent.component &&
        abs(mousePressedEvent.point.x - mouseReleasedEvent.point.x) < sensitivity &&
        abs(mousePressedEvent.point.y - mouseReleasedEvent.point.y) < sensitivity
      ) {
        val hyperlink = findHyperlink(event)
        val action = hyperlink?.link?.action
        if (action != null) {
          runCatching {
            SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
              action(event)
            }
          }.getOrLogException { e ->
            LOG.error("The hyperlink handler threw an exception, hyperlink = $hyperlink", e)
          }
          effectSupport.linkFollowed(hyperlink.highlighter)
          event.consume()
        }
      }
    }

    override fun mouseExited(event: EditorMouseEvent) {
      effectSupport.linkHovered(null)
    }
  }

  private inner class MyMouseMotionListener : EditorMouseMotionListener {
    override fun mouseMoved(e: EditorMouseEvent) {
      val highlightedLink = findHyperlink(e)
      if (highlightedLink?.link?.action == null) {
        editor.setCustomCursor(EditorHyperlinkApplier::class.java, null)
      }
      else {
        editor.setCustomCursor(EditorHyperlinkApplier::class.java, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        effectSupport.linkHovered(highlightedLink.highlighter)
      }
    }
  }

  private class MyEffectSupplier : EditorHyperlinkEffectSupplier {
    override fun getFollowedHyperlinkAttributes(highlighter: RangeHighlighterEx): TextAttributes? {
      return highlighter.getHyperlink().followedAttributes
    }

    override fun getHoveredHyperlinkAttributes(highlighter: RangeHighlighterEx): TextAttributes? {
      return highlighter.getHyperlink().hoveredAttributes
    }
  }
}

private data class HighlightedLink(val link: Hyperlink, val highlighter: RangeHighlighterEx)

private fun RangeHighlighterEx.getHyperlink(): Hyperlink = checkNotNull(getHyperlinkOrNull()) {
  "Highlighter provided to EditorHyperlinkEffectSupport doesn't have a hyperlink, highlighter = $this"
}

private fun RangeHighlighterEx.getHyperlinkOrNull(): Hyperlink? = getUserData(HYPERLINK)

private val HYPERLINK: Key<Hyperlink> = Key.create("HYPERLINK")
private val LOG = logger<EditorHyperlinkApplier>()
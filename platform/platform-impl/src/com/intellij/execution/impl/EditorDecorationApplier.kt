@file:ApiStatus.Experimental
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.getOrHandleException
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.SlowOperations
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.event.MouseEvent
import kotlin.math.abs

/**
 * A helper to apply decorations (hyperlinks, highlightings) to an editor.
 *
 * This interface does UI-only frontend-only part of the [EditorHyperlinkSupport]'s job.
 * The client is responsible for providing decorations created by builder functions
 * and for their removal once they're no longer needed.
 *
 * An instance is created using the [createDecorationApplier] function.
 *
 * @see buildHyperlink
 * @see buildHighlighting
 */
@ApiStatus.Experimental
sealed interface EditorDecorationApplier {
  /**
   * Adds the provided decorations to the editor.
   *
   * @see buildHyperlink
   * @see buildHighlighting
   */
  fun addDecorations(decorations: Collection<EditorDecoration>)

  /**
   * Removes the decorations with the provided IDs from the editor.
   */
  fun removeDecorations(decorationIds: Collection<EditorDecorationId>)

  /**
   * Returns the currently hovered hyperlink, if any.
   */
  fun getHoveredHyperlink(): HyperlinkDecoration?
}

/**
 * Creates a new decoration applier for the given editor.
 *
 * A parent disposable ensures that the applier unsubscribes from the editor listeners.
 */
@ApiStatus.Experimental
fun createDecorationApplier(editor: EditorEx, parentDisposable: Disposable): EditorDecorationApplier =
  EditorDecorationApplierImpl(editor, parentDisposable)

/** The base interface for editor decorations. */
@ApiStatus.Experimental
sealed interface EditorDecoration {
  val id: EditorDecorationId
}

/**
 * A decoration ID, must be unique within the editor.
 *
 * IDs are assigned by the caller, usually using an `AtomicLong` and [createTextDecorationId].
 */
@ApiStatus.Experimental
interface EditorDecorationId : Comparable<EditorDecorationId> {
  val value: Long
}

/**
 * A hyperlink.
 *
 * A highlighted clickable region.
 */
@ApiStatus.Experimental
sealed interface HyperlinkDecoration : EditorDecoration

/**
 * A highlighting.
 *
 * A highlighted region.
 */
@ApiStatus.Experimental
sealed interface HighlightingDecoration : EditorDecoration

/**
 * An inlay.
 *
 * A custom element inserted into the text.
 */
@ApiStatus.Experimental
sealed interface InlayDecoration : EditorDecoration

/**
 * Creates a new ID with the given value.
 */
@ApiStatus.Experimental
fun createTextDecorationId(value: Long) : EditorDecorationId = EditorDecorationIdImpl(value)

/**
 * Builds a new hyperlink.
 *
 * @param id the hyperlink ID, unique within a given editor for all decorations combined
 * @param startOffset the start offset within the document
 * @param endOffset the end offset within the document
 * @param attributes the text attributes of a non-followed, non-hovered link, if `null` the default hyperlink attributes will be used
 * @param action the action to execute on click
 * @param builder the code block that, if not `null`, will be executed on a [HyperlinkBuilder] to set optional attributes
 */
@ApiStatus.Experimental
fun buildHyperlink(
  id: EditorDecorationId,
  startOffset: Int,
  endOffset: Int,
  attributes: TextAttributes?,
  action: (EditorMouseEvent) -> Unit,
  builder: (HyperlinkBuilder.() -> Unit)? = null,
) : HyperlinkDecoration = HyperlinkBuilderImpl(id, startOffset, endOffset, attributes, action).run {
  builder?.invoke(this)
  build()
}

/**
 * A builder to set optional attributes of a hyperlink.
 *
 * An instance of a builder is passed to the function passed to [buildHyperlink] as the last parameter.
 */
@ApiStatus.Experimental
sealed interface HyperlinkBuilder {
  /**
   * The text attributes of a link that was followed (clicked).
   *
   * If `null`, the editor will fall back to the default followed hyperlink attributes.
   */
  var followedAttributes: TextAttributes?
  /**
   * The text attributes of a link that is currently hovered.
   *
   * If `null`, the attributes won't change on hover.
   */
  var hoveredAttributes: TextAttributes?
  /**
   * The highlighting layer.
   *
   * Normally just [HighlighterLayer.HYPERLINK], but a different value can be provided by an external source.
   */
  var layer: Int
}

/**
 * Builds a new highlighting.
 *
 * @param id the highlighting ID, unique within a given editor for all decorations combined
 * @param startOffset the start offset within the document
 * @param endOffset the end offset within the document
 * @param attributes the text attributes of the highlighted region
 * @param builder the code block that, if not `null`, will be executed on a [HighlightingBuilder] to set optional attributes
 */
@ApiStatus.Experimental
fun buildHighlighting(
  id: EditorDecorationId,
  startOffset: Int,
  endOffset: Int,
  attributes: TextAttributes,
  builder: (HighlightingBuilder.() -> Unit)? = null,
) : HighlightingDecoration = HighlightingBuilderImpl(id, startOffset, endOffset, attributes).run {
  builder?.invoke(this)
  build()
}

/**
 * A builder to set optional attributes of a highlighting.
 *
 * An instance of a builder is passed to the function passed to [buildHighlighting] as the last parameter.
 */
@ApiStatus.Experimental
sealed interface HighlightingBuilder {
  /**
   * The highlighting layer.
   *
   * Normally just [HighlighterLayer.CONSOLE_FILTER], but a different value can be provided by an external source.
   */
  var layer: Int
}

/**
 * Builds a new inlay.
 *
 * @param id the inlay ID, unique within a given editor for all decorations combined
 * @param offset the inlay position within the document
 * @param inlayProvider the provider that will be used to create the inlay
 * @param builder reserved for future use
 */
@ApiStatus.Experimental
fun buildInlay(
  id: EditorDecorationId,
  offset: Int,
  inlayProvider: InlayProvider,
  builder: (InlayBuilder.() -> Unit)? = null,
) : InlayDecoration = InlayBuilderImpl(id, offset, inlayProvider).run {
  builder?.invoke(this)
  build()
}

/**
 * An inlay builder.
 *
 * Reserved for future use in case inlays get optional attributes.
 */
@ApiStatus.Experimental
sealed interface InlayBuilder

private class HyperlinkBuilderImpl(
  private val id: EditorDecorationId,
  private val startOffset: Int,
  private val endOffset: Int,
  private val attributes: TextAttributes?,
  private val action: (EditorMouseEvent) -> Unit,
) : HyperlinkBuilder {
  override var followedAttributes: TextAttributes? = null
  override var hoveredAttributes: TextAttributes? = null
  override var layer: Int = HighlighterLayer.HYPERLINK

  fun build(): HyperlinkDecoration = HyperlinkOrHighlightingImpl(
    id = id,
    start = startOffset,
    end = endOffset,
    attributes = attributes,
    followedAttributes = followedAttributes,
    hoveredAttributes = hoveredAttributes,
    layer = layer,
    action = action,
  )
}

private class HighlightingBuilderImpl(
  private val id: EditorDecorationId,
  private val startOffset: Int,
  private val endOffset: Int,
  private val attributes: TextAttributes,
) : HighlightingBuilder {
  override var layer: Int = HighlighterLayer.CONSOLE_FILTER

  fun build(): HighlightingDecoration = HyperlinkOrHighlightingImpl(
    id = id,
    start = startOffset,
    end = endOffset,
    attributes = attributes,
    layer = layer,
  )
}

private class InlayBuilderImpl(
  private val id: EditorDecorationId,
  private val offset: Int,
  private val inlayProvider: InlayProvider,
) : InlayBuilder {
  fun build(): InlayDecoration = InlayDecorationImpl(
    id = id,
    offset,
    inlayProvider = inlayProvider,
  )
}

private data class EditorDecorationIdImpl(override val value: Long) : EditorDecorationId {
  override fun compareTo(other: EditorDecorationId): Int = value.compareTo(other.value)
  override fun toString(): String = value.toString()
}

private data class HyperlinkOrHighlightingImpl(
  override val id: EditorDecorationId,
  val start: Int,
  val end: Int,
  val attributes: TextAttributes?,
  val followedAttributes: TextAttributes? = null,
  val hoveredAttributes: TextAttributes? = null,
  val layer: Int,
  val action: ((EditorMouseEvent) -> Unit)? = null,
) : HyperlinkDecoration, HighlightingDecoration

private data class InlayDecorationImpl(
  override val id: EditorDecorationId,
  val offset: Int,
  val inlayProvider: InlayProvider,
) : InlayDecoration

private class EditorDecorationApplierImpl(private val editor: EditorEx, parentDisposable: Disposable) : EditorDecorationApplier {
  private val highlightersById = hashMapOf<EditorDecorationId, RangeHighlighterEx>()
  private val inlaysById = hashMapOf<EditorDecorationId, com.intellij.openapi.editor.Inlay<*>>()
  private val effectSupport = EditorHyperlinkEffectSupport(editor, MyEffectSupplier())
  private var hoveredHyperlink: HyperlinkDecoration? = null

  init {
    editor.addEditorMouseListener(MyMouseListener(), parentDisposable)
    editor.addEditorMouseMotionListener(MyMouseMotionListener(), parentDisposable)
  }

  override fun addDecorations(decorations: Collection<EditorDecoration>) {
    val inlays = mutableListOf<InlayDecorationImpl>()
    for (decoration in decorations) {
      if(decoration.id in highlightersById) {
        LOG.warn("There's already a highlighter with the id ${decoration.id}")
        continue
      }
      if(decoration.id in inlaysById) {
        LOG.warn("There's already an inlay with the id ${decoration.id}")
        continue
      }
      when (decoration) {
        is HyperlinkOrHighlightingImpl -> addHyperlinkOrHighlighting(decoration)
        is InlayDecorationImpl -> inlays += decoration
      }
    }
    addInlays(inlays)
  }

  override fun removeDecorations(decorationIds: Collection<EditorDecorationId>) {
    for (id in decorationIds) {
      removeDecoration(id)
    }
  }

  override fun getHoveredHyperlink(): HyperlinkDecoration? = hoveredHyperlink

  private fun addHyperlinkOrHighlighting(decoration: HyperlinkOrHighlightingImpl) {
    editor.markupModel.addRangeHighlighterAndChangeAttributes(
      if (decoration.action != null) CodeInsightColors.HYPERLINK_ATTRIBUTES else null,
      decoration.start,
      decoration.end,
      decoration.layer,
      HighlighterTargetArea.EXACT_RANGE,
      false,
    ) { highlighter ->
      if (decoration.attributes != null) {
        highlighter.textAttributes = decoration.attributes
      }
      highlighter.putUserData(HYPERLINK_OR_HIGHLIGHTING, decoration)
      highlightersById[decoration.id] = highlighter
    }
  }

  private fun addInlays(inlays: Collection<InlayDecorationImpl>) {
    if (inlays.isEmpty()) return
    editor.inlayModel.execute(inlays.size > 100) {
      for (inlayFromFilter in inlays) {
        val inlay = inlayFromFilter.inlayProvider.createInlay(editor, inlayFromFilter.offset)
        if (inlay != null) {
          inlaysById[inlayFromFilter.id] = inlay
        }
      }
    }
  }

  private fun removeDecoration(decorationId: EditorDecorationId) {
    val highlighter = highlightersById.remove(decorationId)
    val inlay = inlaysById.remove(decorationId)
    when {
      highlighter == null && inlay == null -> LOG.warn("Decoration ${decorationId} not found")
      highlighter != null && inlay != null -> LOG.warn("The ID ${decorationId} corresponds to both an inlay and a highlighter")
    }
    if (highlighter != null) {
      editor.markupModel.removeHighlighter(highlighter)
    }
    if (inlay != null) {
      Disposer.dispose(inlay)
    }
  }

  private fun findDecoration(event: EditorMouseEvent): HighlightedDecoration? {
    if (event.area != EditorMouseEventArea.EDITING_AREA || !event.isOverText) return null
    return findDecoration(event.offset)
  }

  private fun findDecoration(offset: Int): HighlightedDecoration? {
    var result: HighlightedDecoration? = null
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
        result = HighlightedDecoration(hyperlink, highlighter)
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
        val hyperlink = findDecoration(event)
        val action = hyperlink?.link?.action
        if (action != null) {
          runCatching {
            SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
              action(event)
            }
          }.getOrHandleException { e ->
            LOG.error("The hyperlink handler threw an exception, hyperlink = $hyperlink", e)
          }
          effectSupport.linkFollowed(hyperlink.highlighter)
          event.consume()
        }
      }
    }

    override fun mouseExited(event: EditorMouseEvent) {
      effectSupport.linkHovered(null)
      hoveredHyperlink = null
    }
  }

  private inner class MyMouseMotionListener : EditorMouseMotionListener {
    override fun mouseMoved(e: EditorMouseEvent) {
      val highlightedLink = findDecoration(e)
      if (highlightedLink?.link?.action == null) {
        editor.setCustomCursor(EditorDecorationApplierImpl::class.java, null)
        effectSupport.linkHovered(null)
        hoveredHyperlink = null
      }
      else {
        editor.setCustomCursor(EditorDecorationApplierImpl::class.java, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        effectSupport.linkHovered(highlightedLink.highlighter)
        hoveredHyperlink = highlightedLink.link
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

private data class HighlightedDecoration(val link: HyperlinkOrHighlightingImpl, val highlighter: RangeHighlighterEx)

private fun RangeHighlighterEx.getHyperlink(): HyperlinkOrHighlightingImpl = checkNotNull(getHyperlinkOrNull()) {
  "Highlighter provided to EditorHyperlinkEffectSupport doesn't have a hyperlink, highlighter = $this"
}

private fun RangeHighlighterEx.getHyperlinkOrNull(): HyperlinkOrHighlightingImpl? = getUserData(HYPERLINK_OR_HIGHLIGHTING)

private val HYPERLINK_OR_HIGHLIGHTING: Key<HyperlinkOrHighlightingImpl> = Key.create("HYPERLINK_OR_HIGHLIGHTING")

private val LOG = logger<EditorDecorationApplierImpl>()

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl


import com.intellij.execution.impl.EditorHyperlinkUsageCollector.HyperlinkFollowedPlace
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.platform.eel.isMac
import com.intellij.platform.eel.provider.localEel
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Cursor
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

internal class EditorHyperlinkInteraction(
  private val editor: EditorEx,
  private val effectSupplier: EditorHyperlinkEffectSupplier,
  parentDisposable: Disposable,
) {

  private val hintManager: InvisibleHyperlinkHintManager = InvisibleHyperlinkHintManager(editor, parentDisposable)
  private var followedLinkWrapper: ChangedAttrsLinkWrapper? = null
  private var hoveredLinkWrapper: ChangedAttrsLinkWrapper? = null

  val lastFollowedLink: RangeHighlighter?
    @RequiresEdt(generateAssertion = false)
    get() = followedLinkWrapper?.linkRangeHighlighter

  init {
    editor.contentComponent.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        hoveredLinkWrapper?.linkRangeHighlighter?.let {
          if (effectSupplier.isInvisibleLink(it) && e.isCtrlOnly) {
            linkHovered(it, true)
          }
        }
      }

      override fun keyReleased(e: KeyEvent) {
        hoveredLinkWrapper?.linkRangeHighlighter?.let {
          if (effectSupplier.isInvisibleLink(it) && e.isCtrlOnly) {
            linkHovered(it, false)
          }
        }
      }
    })
  }

  /**
   * @param link a highlighter that is already in the editor, expected to be valid
   */
  @RequiresEdt(generateAssertion = false)
  fun followLink(link: RangeHighlighterEx, event: EditorMouseEvent, action: () -> Unit) {
    if (effectSupplier.isInvisibleLink(link) && !event.isCtrlPressed) {
      hintManager.showHint(link, event, action)
    }
    else {
      action()
      onLinkFollowed(link)
      if (effectSupplier.isInvisibleLink(link)) {
        EditorHyperlinkUsageCollector.logInvisibleHyperlinkFollowed(HyperlinkFollowedPlace.EDITOR_LINK_CTRL_CLICKED)
      }
      event.consume()
    }
  }

  fun onLinkFollowed(link: RangeHighlighterEx) {
    if (followedLinkWrapper?.isSame(link) == true) return
    followedLinkWrapper?.restoreOriginalAttrs()
    followedLinkWrapper = null

    if (effectSupplier.isInvisibleLink(link)) {
      // invisible links don't have follow effect
      return
    }
    // When a link is followed, remove any hovered links.
    hoveredLinkWrapper?.restoreOriginalAttrs()
    hoveredLinkWrapper = null
    followedLinkWrapper = ChangedAttrsLinkWrapper(
      link,
      effectSupplier.getFollowedHyperlinkAttributes(link) ?: defaultFollowedHyperlinkAttributes(),
      false,
      false,
    )
  }

  @RequiresEdt(generateAssertion = false)
  fun linkHovered(link: RangeHighlighter?, e: EditorMouseEvent) {
    hintManager.onHoveredLinkChange(link, e)
    if (hintManager.isInsideHint(e)) {
      linkHovered(null, false)
    }
    else {
      linkHovered(link, e.isCtrlPressed)
    }
  }

  private fun setMouseCursor(hand: Boolean) {
    val cursor = if (hand) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else null
    editor.setCustomCursor(EditorHyperlinkInteraction::class.java, cursor)
  }

  @RequiresEdt(generateAssertion = false)
  private fun linkHovered(link: RangeHighlighter?, ctrlPressed: Boolean) {
    if (link == null || link !is RangeHighlighterEx) {
      setMouseCursor(false)
      hoveredLinkWrapper?.restoreOriginalAttrs()
      hoveredLinkWrapper = null
      return
    }
    val invisibleLink = effectSupplier.isInvisibleLink(link)
    setMouseCursor(!invisibleLink || ctrlPressed)
    if (hoveredLinkWrapper?.isSame(link, ctrlPressed) == true) {
      return  // the link is already shown as hovered
    }
    if (followedLinkWrapper?.isSame(link) == true) {
      return  // if the link is shown as followed already, no hover effect should be applied
    }
    hoveredLinkWrapper?.restoreOriginalAttrs()
    hoveredLinkWrapper = null

    val hoveredLinkAttrs = calcHoveredLinkAttrs(link, invisibleLink, ctrlPressed)
    if (hoveredLinkAttrs != null) {
      hoveredLinkWrapper = ChangedAttrsLinkWrapper(link, hoveredLinkAttrs, invisibleLink, ctrlPressed)
    }
  }

  private fun calcHoveredLinkAttrs(link: RangeHighlighterEx, invisibleLink: Boolean, ctrlPressed: Boolean): TextAttributes? {
    return if (invisibleLink) {
      if (ctrlPressed) {
        editor.colorsScheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES)
      }
      else {
        effectSupplier.getHoveredHyperlinkAttributes(link) ?: getDefaultHoveredLinkAttrs()
      }
    }
    else {
      effectSupplier.getHoveredHyperlinkAttributes(link)
    }
  }

  private fun getDefaultHoveredLinkAttrs(): TextAttributes {
    val effectColor = ColorUtil.withAlpha(
      editor.colorsScheme.defaultForeground,
      if (JBColor.isBright()) 0.4 else 0.5
    )
    return TextAttributes(null, null, effectColor, EffectType.LINE_UNDERSCORE, Font.PLAIN)
  }

  private inner class ChangedAttrsLinkWrapper(
    val linkRangeHighlighter: RangeHighlighterEx,
    newTextAttrs: TextAttributes,
    private val invisibleLink: Boolean,
    private val ctrlPressed: Boolean,
  ) {
    private val originalTextAttrs: TextAttributes? = linkRangeHighlighter.getTextAttributes(editor.getColorsScheme())

    init {
      if (linkRangeHighlighter.isValid) {
        linkRangeHighlighter.setTextAttributes(newTextAttrs)
      }
    }

    fun isSame(linkRangeHighlighter: RangeHighlighter): Boolean = this.linkRangeHighlighter === linkRangeHighlighter

    fun isSame(linkRangeHighlighter: RangeHighlighter, ctrlPressed: Boolean): Boolean {
      return this.linkRangeHighlighter === linkRangeHighlighter &&
             (!invisibleLink || this.ctrlPressed == ctrlPressed)
    }

    fun restoreOriginalAttrs() {
      if (linkRangeHighlighter.isValid()) {
        linkRangeHighlighter.setTextAttributes(originalTextAttrs)
      }
    }
  }
}

private fun defaultFollowedHyperlinkAttributes(): TextAttributes =
  EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES)

private val EditorMouseEvent.isCtrlPressed: Boolean
  get() = if (localEel.platform.isMac) mouseEvent.isMetaDown else mouseEvent.isControlDown

private val KeyEvent.isCtrlOnly: Boolean
  get() {
    val ctrlModifier = if (localEel.platform.isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL
    return keyCode == ctrlModifier
  }

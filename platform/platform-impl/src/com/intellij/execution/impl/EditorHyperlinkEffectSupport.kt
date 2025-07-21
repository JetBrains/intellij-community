// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt

internal interface EditorHyperlinkEffectSupplier {
  fun getFollowedHyperlinkAttributes(highlighter: RangeHighlighterEx): TextAttributes?
  fun getHoveredHyperlinkAttributes(highlighter: RangeHighlighterEx): TextAttributes?
}

internal class EditorHyperlinkEffectSupport(
  private val editor: Editor,
  private val effectSupplier: EditorHyperlinkEffectSupplier,
) {

  private var followedLinkWrapper: ChangedAttrsLinkWrapper? = null
  private var hoveredLinkWrapper: ChangedAttrsLinkWrapper? = null

  val followedLink: RangeHighlighter?
    @RequiresEdt(generateAssertion = false)
    get() = followedLinkWrapper?.linkRangeHighlighter

  @RequiresEdt(generateAssertion = false)
  fun linkFollowed(link: RangeHighlighter) {
    if (followedLinkWrapper?.isSame(link) == true) return
    followedLinkWrapper?.restoreOriginalAttrs()
    followedLinkWrapper = null
    // When a link is followed, remove any hovered links.
    hoveredLinkWrapper?.restoreOriginalAttrs()
    hoveredLinkWrapper = null
    if (link is RangeHighlighterEx) {
      followedLinkWrapper = ChangedAttrsLinkWrapper(
        link,
        effectSupplier.getFollowedHyperlinkAttributes(link) ?: defaultFollowedHyperlinkAttributes()
      )
    }
  }

  @RequiresEdt(generateAssertion = false)
  fun linkHovered(link: RangeHighlighter?) {
    ThreadingAssertions.assertEventDispatchThread()
    if (link == null) {
      hoveredLinkWrapper?.restoreOriginalAttrs()
      hoveredLinkWrapper = null
      return
    }
    if (hoveredLinkWrapper?.isSame(link) == true) {
      return  // the link is already shown as hovered
    }
    if (followedLinkWrapper?.isSame(link) == true) {
      return  // if the link is shown as followed already, no hover effect should be applied
    }
    hoveredLinkWrapper?.restoreOriginalAttrs()
    hoveredLinkWrapper = null
    if (link is RangeHighlighterEx) {
      val hoveredLinkAttrs = effectSupplier.getHoveredHyperlinkAttributes(link)
      if (hoveredLinkAttrs != null) {
        hoveredLinkWrapper = ChangedAttrsLinkWrapper(link, hoveredLinkAttrs)
      }
    }
  }

  private inner class ChangedAttrsLinkWrapper(val linkRangeHighlighter: RangeHighlighterEx, newTextAttrs: TextAttributes) {
    private val originalTextAttrs: TextAttributes? = linkRangeHighlighter.getTextAttributes(editor.getColorsScheme())

    init {
      linkRangeHighlighter.setTextAttributes(newTextAttrs)
    }

    fun isSame(linkRangeHighlighter: RangeHighlighter): Boolean = this.linkRangeHighlighter === linkRangeHighlighter

    fun restoreOriginalAttrs() {
      if (linkRangeHighlighter.isValid()) {
        linkRangeHighlighter.setTextAttributes(originalTextAttrs)
      }
    }
  }
}

private fun defaultFollowedHyperlinkAttributes(): TextAttributes =
  EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES)

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.painting

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorPaintingTestCase
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.TestDataPath
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

@TestDataPath("\$CONTENT_ROOT/testData/diff/painting")
class DiffEditorPaintingTest : EditorPaintingTestCase() {
  override fun getTestDataPath(): String {
    return "platform/diff-impl/tests/testData/diff/painting"
  }

  fun testWholeLineChanged() {
    initText("foo")
    DiffDrawUtil.createHighlighter(editor, 0, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testOneLetterChanged() {
    initText("foo")
    DiffDrawUtil.createHighlighter(editor, 0, 1, MyDiffType, true)
    DiffDrawUtil.createInlineHighlighter(editor, 1, 2, MyDiffType)
    checkResultWithGutter()
  }

  fun testNewlineChanged() {
    initText("fo\nbar")
    DiffDrawUtil.createHighlighter(editor, 0, 2, MyDiffType, true)
    DiffDrawUtil.createInlineHighlighter(editor, 2, 3, MyDiffType)
    checkResultWithGutter()
  }

  fun testLastLineChanged() {
    initText("foo\nx")
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testLastEmptyLineChanged() {
    initText("foo\n")
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testLastLineSoftWraps() {
    initText("foo ba biz")
    configureSoftWraps(4)
    DiffDrawUtil.createHighlighter(editor, 0, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testEmptyRange() {
    initText("foo\nbar")
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testEmptyRangeFirstLine() {
    initText("foo\nbar")
    DiffDrawUtil.createHighlighter(editor, 0, 0, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testEmptyRangeLastEmptyLine() {
    initText("foo")
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testEmptyRangeLastLineSoftWraps() {
    initText("foo ba biz")
    configureSoftWraps(4)
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testLineMarker() {
    initText("fo o\nba r")
    configureSoftWraps(2)

    DiffDrawUtil.createLineMarker(editor, 1, MyDiffType)
    checkResultWithGutter()
  }

  fun testBorderLineMarker() {
    initText("fo o\nba r")
    configureSoftWraps(2)

    DiffDrawUtil.createBorderLineMarker(editor, 1, SeparatorPlacement.BOTTOM)
    DiffDrawUtil.createBorderLineMarker(editor, 1, SeparatorPlacement.TOP)
    checkResultWithGutter()
  }

  fun testEmptyRangeWithInlayBelow1() {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(5, true, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testEmptyRangeWithInlayBelow2() {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(3, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testRangeWithInlayBelow1() {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(9, true, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testRangeWithInlayBelow2() {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(7, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testEmptyRangeWithInlayAbove1() {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(5, false, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testEmptyRangeWithInlayAbove2() {
    initText("foo\nbar")
    editor.inlayModel.addBlockElement(2, false, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 1, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testRangeWithInlayAbove1() {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(5, false, true, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testRangeWithInlayAbove2() {
    initText("foo\nbar\nbiz")
    editor.inlayModel.addBlockElement(2, false, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 2, MyDiffType, false)
    checkResultWithGutter()
  }

  fun testRangeWithInlayInside() {
    initText("foo\nbar\nbiz\nbuzz")
    editor.inlayModel.addBlockElement(7, true, false, 0, MyInlayRenderer())
    DiffDrawUtil.createHighlighter(editor, 1, 3, MyDiffType, false)
    checkResultWithGutter()
  }

  private object MyDiffType : TextDiffType {
    override fun getName(): String = throw UnsupportedOperationException()
    override fun getColor(editor: Editor?): Color = Color.RED
    override fun getIgnoredColor(editor: Editor?): Color = Color.BLUE
    override fun getMarkerColor(editor: Editor?): Color? = Color.GREEN
  }

  private class MyInlayRenderer : EditorCustomElementRenderer {
    override fun calcWidthInPixels(inlay: Inlay<*>): Int = 5

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
      g.color = JBColor.MAGENTA
      g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
    }
  }
}


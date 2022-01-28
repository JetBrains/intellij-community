// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.codeInsight.navigation.CtrlMouseHandler
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.TestTimeOut
import com.intellij.util.ui.UIUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import java.util.stream.Collectors
import java.util.stream.Stream

class CtrlMouseHandlerTest : AbstractEditorTest() {

  fun testHighlighterDisappearsOnMouseMovingAway() {
    init("class A {}", JavaFileType.INSTANCE)
    val mouse = mouse()
    mouse.ctrl().moveTo(0, 6)
    assertHighlighted(6, 7)
    mouse.moveTo(0, 0)
    assertHighlighted()
  }

  // input parameters should have the following form:
  // firstHighlighterStartOffset, firstHighlighterEndOffset, secondHighlighterStartOffset, secondHighlighterEndOffset, ...
  private fun assertHighlighted(vararg offsets: Int) {
    assert(offsets.size % 2 == 0)
    val highlighterCount = offsets.size / 2
    waitForHighlighting()
    val highlighters = getCurrentHighlighters()
    assertEquals("Unexpected number of highlighters", highlighterCount, highlighters.size)
    for (i in 0 until highlighterCount) {
      assertEquals("Unexpected start of " + (i + 1) + " highlighter", offsets[i * 2], highlighters[i].startOffset)
      assertEquals("Unexpected end of " + (i + 1) + " highlighter", offsets[i * 2 + 1], highlighters[i].endOffset)
    }
  }

  private fun getCurrentHighlighters(): List<RangeHighlighter> {
    val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
    return Stream.of(*editor.markupModel.allHighlighters)
      .filter { h: RangeHighlighter ->
        attributes == h.getTextAttributes(null) || EditorColors.REFERENCE_HYPERLINK_COLOR == h.textAttributesKey
      }
      .sorted(RangeMarker.BY_START_OFFSET)
      .collect(Collectors.toList())
  }

  private fun waitForHighlighting() {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    UIUtil.dispatchAllInvocationEvents()
    val handler = project.getService(CtrlMouseHandler::class.java)
    val t = TestTimeOut.setTimeout(1, TimeUnit.MINUTES)
    while (handler.isCalculationInProgress) {
      if (t.timedOut()) throw RuntimeException("Timed out waiting for CtrlMouseHandler")
      LockSupport.parkNanos(10_000_000)
      UIUtil.dispatchAllInvocationEvents()
    }
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.codeInsight.navigation.CtrlMouseHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.util.TestTimeOut;
import com.intellij.util.ui.UIUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CtrlMouseHandlerTest extends AbstractEditorTest {
  public void testHighlighterDisappearsOnMouseMovingAway() {
    init("class A {}", JavaFileType.INSTANCE);
    EditorMouseFixture mouse = mouse();
    mouse.ctrl().moveTo(0, 6);
    assertHighlighted(6, 7);
    mouse.moveTo(0, 0);
    assertHighlighted();
  }

  // input parameters should have the following form:
  // firstHighlighterStartOffset, firstHighlighterEndOffset, secondHighlighterStartOffset, secondHighlighterEndOffset, ...
  private void assertHighlighted(int... offsets) {
    assert offsets.length % 2 == 0;
    int highlighterCount = offsets.length / 2;
    waitForHighlighting();
    List<RangeHighlighter> highlighters = getCurrentHighlighters();
    assertEquals("Unexpected number of highlighters", highlighterCount, highlighters.size());
    for (int i = 0; i < highlighterCount; i++) {
      assertEquals("Unexpected start of " + (i + 1) + " highlighter", offsets[i * 2], highlighters.get(i).getStartOffset());
      assertEquals("Unexpected end of " + (i + 1) + " highlighter", offsets[i * 2 + 1], highlighters.get(i).getEndOffset());
    }
  }

  private List<RangeHighlighter> getCurrentHighlighters() {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR);
    return Stream.of(getEditor().getMarkupModel().getAllHighlighters())
      .filter(h -> attributes.equals(h.getTextAttributes(null)) || EditorColors.REFERENCE_HYPERLINK_COLOR.equals(h.getTextAttributesKey()))
      .sorted(RangeMarker.BY_START_OFFSET)
      .collect(Collectors.toList());
  }

  private void waitForHighlighting() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    UIUtil.dispatchAllInvocationEvents();

    CtrlMouseHandler handler = getProject().getService(CtrlMouseHandler.class);
    TestTimeOut t = TestTimeOut.setTimeout(1, TimeUnit.MINUTES);
    while (handler.isCalculationInProgress()) {
      if (t.timedOut()) throw new RuntimeException("Timed out waiting for CtrlMouseHandler");
      LockSupport.parkNanos(10_000_000);
      UIUtil.dispatchAllInvocationEvents();
    }
  }
}

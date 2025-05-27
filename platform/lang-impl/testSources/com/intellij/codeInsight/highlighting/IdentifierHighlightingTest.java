// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class IdentifierHighlightingTest extends LightPlatformCodeInsightFixture4TestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setReadEditorMarkupModel(true);
  }

  @Test
  public void testIdentifierHighlightingCachingWorksAndReturnsResultsExactlyTheSameAsIdentifierHighlightingComputer() {
    @Language("JAVA")
    String text = """
      package com.intellij.openapi.util;
      
      import org.jetbrains.annotations.Contract;
      
      import java.util.Comparator;
      
      public interface Segment {
        Segment[] EMPTY_ARRAY = new Segment[0];
      
        @Contract(pure = true)
        int getStartOffset();
      
        @Contract(pure = true)
        int getEndOffset();
      
        @SuppressWarnings("ComparatorCombinators")
        Comparator<Segment> BY_START_OFFSET_THEN_END_OFFSET = (r1, r2) -> {
          int result = r1.getStartOffset() - r2.getStartOffset();
          if (result == 0) result = r1.getEndOffset() - r2.getEndOffset();
          return result;
        };
      
        /**
         * @return true if {@link #getStartOffset()} {@code <= offset && offset < } {@link #getEndOffset()}
         */
        @Contract(pure = true)
        default boolean contains(int offset) {
          return getStartOffset() <= offset && offset < getEndOffset();
        }
        /**
         * @return true if {@link #getStartOffset()} {@code <= offset && offset <= } {@link #getEndOffset()}
         */
        @Contract(pure = true)
        default boolean containsInclusive(int offset) {
          return getStartOffset() <= offset && offset <= getEndOffset();
        }
      }
      """;
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(getProject(), () -> {
      myFixture.configureByText("C.java", text);
      myFixture.doHighlighting();
      for (int offset=0; offset<myFixture.getEditor().getDocument().getTextLength();offset++) {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
      for (int offset=myFixture.getEditor().getDocument().getTextLength()-1;offset>=0;offset--) {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
      for (int offset=myFixture.getEditor().getDocument().getTextLength()-1;offset>=0;offset--) {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
      // clear cache
      WriteCommandAction.runWriteCommandAction(getProject(), ()->{
        myFixture.getEditor().getDocument().insertString(0, " ");
        myFixture.getEditor().getDocument().deleteString(0, 1);
      });
      // simulate random hops
      for (int offset = 0,i=0; i < 2*myFixture.getEditor().getDocument().getTextLength(); offset = (offset + 105929/*prime>text length*/) % myFixture.getEditor().getDocument().getTextLength(),i++) {
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
    });
  }

  private void assertIdentifierHighlightingManagerCachingWorksForOffset(int offset) {
    List<HighlightInfo> infos = myFixture.doHighlighting();
    List<HighlightInfo> idents = ContainerUtil.filter(infos, info -> info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY);
    IdentifierHighlightingComputer computer = new IdentifierHighlightingComputer(myFixture.getFile(), myFixture.getEditor(), new ProperTextRange(myFixture.getFile().getTextRange()), offset);
    IdentifierHighlightingResult result = computer.computeRanges();
    Collection<IdentifierOccurrence> occurrences = result.occurrences();
    if (IdentifierHighlighterUpdater.Companion.shouldShowIdentifierHighlightingResult(result, myFixture.getEditor())) {
      assertEqualOccurrences(occurrences, idents, "offset:" + offset + "; infos:" + idents + "; result:" + result);
    }
  }

  private void assertEqualOccurrences(Collection<IdentifierOccurrence> expectedOccurrences, List<HighlightInfo> actualInfos, String msg) {
    assertEquals(msg,expectedOccurrences.size(), actualInfos.size());
    List<IdentifierOccurrence> sortedExpected = ContainerUtil.sorted(expectedOccurrences, (o1, o2) -> Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(o1.range(), o2.range()));
    List<HighlightInfo> sortedActual = ContainerUtil.sorted(actualInfos, Segment.BY_START_OFFSET_THEN_END_OFFSET);
    for (int i = 0; i < sortedActual.size(); i++) {
      HighlightInfo info = sortedActual.get(i);
      IdentifierOccurrence expected = sortedExpected.get(i);
      assertEquals(msg,expected.highlightInfoType(), info.type);
      assertEquals(msg,TextRange.create(expected.range()), TextRange.create(info));
    }
  }
}

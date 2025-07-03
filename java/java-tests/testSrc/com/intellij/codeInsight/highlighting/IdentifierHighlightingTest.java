// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IdentifierHighlightingTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_16); // needed for String.formatted
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk21();
  }

  public void testIdentifierHighlightingCachingWorksAndReturnsResultsExactlyTheSameAsIdentifierHighlightingComputer() {
    @Language("JAVA")
    String text = """
      package com.intellij.openapi.util;
      
      import java.util.Comparator;
      import java.lang.annotation.*;
      
      @Documented
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
      @interface Contract {
         boolean pure() default false;
      }
      
      interface Segment {
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
          String s = "%s %s".formatted("Hello", "World"); // check for individual chunks indide string literal
          return getStartOffset() <= offset && offset <= getEndOffset();
        }
      }
      """;
    IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(getProject(), () -> {
      configureFromFileText("C.java", text);
      assertEmpty(doHighlighting(HighlightSeverity.ERROR));
      for (int offset=0; offset<getEditor().getDocument().getTextLength();offset++) {
        getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
      for (int offset=getEditor().getDocument().getTextLength()-1;offset>=0;offset--) {
        getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
      // clear cache
      WriteCommandAction.runWriteCommandAction(getProject(), ()->{
        getEditor().getDocument().insertString(0, " ");
        getEditor().getDocument().deleteString(0, 1);
      });
      for (int offset=getEditor().getDocument().getTextLength()-1;offset>=0;offset--) {
        getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
      // clear cache
      WriteCommandAction.runWriteCommandAction(getProject(), ()->{
        getEditor().getDocument().insertString(0, " ");
        getEditor().getDocument().deleteString(0, 1);
      });
      // simulate random hops
      for (int offset = 0,i=0; i < 2*getEditor().getDocument().getTextLength(); offset = (offset + 105929/*prime>text length*/) % getEditor().getDocument().getTextLength(),i++) {
        getEditor().getCaretModel().moveToOffset(offset);
        assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
      }
    });
  }

  private void assertIdentifierHighlightingManagerCachingWorksForOffset(int offset) {
    assertEmpty(highlightErrors());
    List<HighlightInfo> infos = new ArrayList<>();
    MarkupModelEx markupModel = (MarkupModelEx)getEditor().getMarkupModel();
    DaemonCodeAnalyzerEx.processHighlights(markupModel, getProject(), null, 0, getEditor().getDocument().getTextLength(),
                                           Processors.cancelableCollectProcessor(infos));
    List<HighlightInfo> identInfos = ContainerUtil.filter(infos, info -> info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY);
    IdentifierHighlightingResult result = CodeInsightTestUtil.runIdentifierHighlighterPass(getFile(), getEditor());
    Collection<IdentifierOccurrence> occurrences = result.occurrences();
    if (IdentifierHighlighterUpdater.Companion.shouldShowIdentifierHighlightingResult(result, getEditor())) {
      assertEqualOccurrences(occurrences, identInfos, "offset:" + offset + "; infos:" + identInfos + "; result:" + result + "; text:\n----\n" +
                                                  StringUtil.first(getEditor().getDocument().getText().substring(offset), 100, true)+"\n----");
    }
  }

  private static void assertEqualOccurrences(Collection<IdentifierOccurrence> expectedOccurrences,
                                             List<? extends HighlightInfo> actualInfos,
                                             String msg) {
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

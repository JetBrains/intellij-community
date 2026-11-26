// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterUpdater;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingResult;
import com.intellij.codeInsight.daemon.impl.IdentifierOccurrence;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.Processors;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class IdentifierHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk21()));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @BeforeEach
  void setUp() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_16); // needed for String.formatted
  }

  @Test
  public void testIdentifierHighlightingCachingWorksAndReturnsResultsExactlyTheSameAsIdentifierHighlightingComputer() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
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
    EdtTestUtil.runInEdtAndWait(() ->
      IdentifierHighlighterPassFactory.doWithIdentifierHighlightingEnabled(myFixture.getProject(), () -> {
        daemonFixture.get().configureFromFileText("C.java", text);
        List<HighlightInfo> errors = daemonFixture.get().doHighlighting(HighlightSeverity.ERROR);
        assertTrue(errors.isEmpty(), errors.toString());
        for (int offset=0; offset<myFixture.getEditor().getDocument().getTextLength();offset++) {
          myFixture.getEditor().getCaretModel().moveToOffset(offset);
          assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
        }
        for (int offset=myFixture.getEditor().getDocument().getTextLength()-1;offset>=0;offset--) {
          myFixture.getEditor().getCaretModel().moveToOffset(offset);
          assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
        }
        // clear cache
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), ()->{
          myFixture.getEditor().getDocument().insertString(0, " ");
          myFixture.getEditor().getDocument().deleteString(0, 1);
        });
        for (int offset=myFixture.getEditor().getDocument().getTextLength()-1;offset>=0;offset--) {
          myFixture.getEditor().getCaretModel().moveToOffset(offset);
          assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
        }
        // clear cache
        WriteCommandAction.runWriteCommandAction(myFixture.getProject(), ()->{
          myFixture.getEditor().getDocument().insertString(0, " ");
          myFixture.getEditor().getDocument().deleteString(0, 1);
        });
        // simulate random hops
        for (int offset = 0,i=0; i < 2*myFixture.getEditor().getDocument().getTextLength(); offset = (offset + 105929/*prime>text length*/) % myFixture.getEditor().getDocument().getTextLength(),i++) {
          myFixture.getEditor().getCaretModel().moveToOffset(offset);
          assertIdentifierHighlightingManagerCachingWorksForOffset(offset);
        }
      }));
  }

  private void assertIdentifierHighlightingManagerCachingWorksForOffset(int offset) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    assertTrue(errors.isEmpty(), errors.toString());
    List<HighlightInfo> infos = new ArrayList<>();
    MarkupModelEx markupModel = (MarkupModelEx)myFixture.getEditor().getMarkupModel();
    DaemonCodeAnalyzerEx.processHighlights(markupModel, myFixture.getProject(), null, 0, myFixture.getEditor().getDocument().getTextLength(),
                                           Processors.cancelableCollectProcessor(infos));
    List<HighlightInfo> identInfos = ContainerUtil.filter(infos, info -> info.getSeverity() == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY);
    IdentifierHighlightingResult result = CodeInsightTestUtil.runIdentifierHighlighterPass(myFixture.getFile(), myFixture.getEditor());
    Collection<IdentifierOccurrence> occurrences = result.occurrences();
    if (IdentifierHighlighterUpdater.Companion.shouldShowIdentifierHighlightingResult(result, myFixture.getEditor())) {
      assertEqualOccurrences(occurrences, identInfos, "offset:" + offset + "; infos:" + identInfos + "; result:" + result + "; text:\n----\n" +
                                                  StringUtil.first(myFixture.getEditor().getDocument().getText().substring(offset), 100, true)+"\n----");
    }
  }

  private static void assertEqualOccurrences(Collection<IdentifierOccurrence> expectedOccurrences,
                                             List<? extends HighlightInfo> actualInfos,
                                             String msg) {
    assertEquals(expectedOccurrences.size(), actualInfos.size(), msg);
    List<IdentifierOccurrence> sortedExpected = ContainerUtil.sorted(expectedOccurrences, (o1, o2) -> Segment.BY_START_OFFSET_THEN_END_OFFSET.compare(o1.range(), o2.range()));
    List<HighlightInfo> sortedActual = ContainerUtil.sorted(actualInfos, Segment.BY_START_OFFSET_THEN_END_OFFSET);
    for (int i = 0; i < sortedActual.size(); i++) {
      HighlightInfo info = sortedActual.get(i);
      IdentifierOccurrence expected = sortedExpected.get(i);
      assertEquals(expected.highlightInfoType(), info.type, msg);
      assertEquals(TextRange.create(expected.range()), TextRange.create(info), msg);
    }
  }
}

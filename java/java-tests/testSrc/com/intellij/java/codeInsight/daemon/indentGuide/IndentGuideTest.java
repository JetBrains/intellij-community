// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;
import com.intellij.openapi.editor.impl.IndentsModelImpl;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IndentGuideTest extends BaseIndentGuideTest {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public void testIndentGuidesWhichCrossCommentedCodeBetweenCommentMarkAndCommentText() {
    // IDEA-99572.
    doTest("""
             class Test {
               void test() {
               |  //test();
               |  if (true) {
               |  |  int i = 1;
               |  | \s
             //|  |    int k;
               |  |  int j = 1;
               |  }
               }
             }
             """);
  }

  public void testNoIndentGuidesInCommentedRegions() {
    doTest("""
             class Test {
               void test() {
               |  return;
             //|    if (true) {
             //|      int i1 = 1;
             //|      int i2 = 2;
             //|      if (true) {
             //|        int j1 = 1;
             //|        int j2 = 2;
             //|      }
             //|    }
             //|  int k = 1;
               }
             }
             """);
  }

  public void testIndentGuideWhichStartsOnCommentLine() {
    doTest("""
             class Test {
               void test(int i) {
               |  switch (i) {
               |  |//
               |  |  case 1:
               |  |  case 2:
               |  }
               }
             }
             """);
  }

  public void testNoIndentGuideForJavadoc() {
    doTest("""
             class Test {
               /**
                * doc
                */
               int i;
             }
             """);
  }

  public void testNoUnnecessaryGuideForNonFirstLineComments() {
    doTest("""
             class Test {
               void test() {
               |  //int i1;
               |  //int i2;
               |  return;
               }
             }
             """);
  }

  public void testBlockCommentAndInnerIndents() {
    doTest("""
             class Test {
               int test() {
               |  return 1 /*{
               |    int test2() {
               |      int i1;
               |    }
               |    int i2;
               |  }*/;
               }
             }
             """);
  }

  public void testEmptyCommentDoesNotBreakIndents() {
    doTest("""
             class Test {
               void m() {
               |
             //|
               |
               |  int v;
               }
             }
             """);
  }

  public void testTabsIndent() {
    doTest("""
             class Test {
             \t\tvoid m() {
             \t\t|
             \t\t|  int v;
             \t\t}
             }
             """);
  }

  public void testCodeConstructStartLine() {
    myFixture.configureByText(getTestName(false) + ".java", """
      class C {
        void m()\s
        {
        <caret>  int a;
        }
      }
      """);
    CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, false);
    IndentGuideDescriptor guide = myFixture.getEditor().getIndentsModel().getCaretIndentGuide();
    assertNotNull(guide);
    assert guide.toString().equals("2 (1-2-4)");
  }

  public void testIndentModel() {
    myFixture.configureByText(getTestName(false) + ".java", """
      class C {
             }""");

    IndentsModelImpl model = (IndentsModelImpl)myFixture.getEditor().getIndentsModel();
    int nLines = myFixture.getEditor().getDocument().getLineCount();
    int nCols = myFixture.getEditor().getDocument().getLineEndOffset(0);

    model.assumeIndents(new ArrayList<>());
    for (int start = 0; start <= nLines; start++) {
      for (int end = start + 1; end <= nLines; end++) {
        final IndentGuideDescriptor descriptor = model.getDescriptor(start, end);
        assertNull("Indent at lines " + start + "-" + end + " is present, but it shouldn't", descriptor);
      }
    }

    for (int start = 0; start <= nLines; start++) {
      for (int end = start + 1; end <= nLines; end++) {
        for (int level = 0; level < nCols; level++) {
          model.assumeIndents(List.of(new IndentGuideDescriptor(level, start, end)));
          final IndentGuideDescriptor descriptor = model.getDescriptor(start, end);
          assertNotNull("Indent at " + level + " for lines " + start + " - " + end + " is missing", descriptor);
          final int actual = descriptor.indentLevel;
          assertEquals("Expected indent at lines " + start + " - " + end + ", level: " + level + ", found at " + actual,
                       level, actual);
        }
      }
    }

    model.assumeIndents(List.of(new IndentGuideDescriptor(0, 0, 1), new IndentGuideDescriptor(1, 1, 2)));
    final IndentGuideDescriptor descriptor = model.getDescriptor(0, 1);
    assertNotNull("Indent at 0 for lines 0 - 1 is missing", descriptor);
    int actual = descriptor.indentLevel;
    assertEquals("Expected indent at lines 0 - 1, level: 0, found at " + actual, 0, actual);
    final IndentGuideDescriptor descriptor1 = model.getDescriptor(1, 2);
    assertNotNull("Indent at 1 for lines 1 - 2 is missing", descriptor1);
    actual = descriptor1.indentLevel;
    assertEquals("Expected indent at lines 1 - 2, level: 1, found at " + actual, 1, actual);
    for (int start = 0; start <= nLines; start++) {
      for (int end = start + 1; end <= nLines; end++) {
        final IndentGuideDescriptor descriptor2 = model.getDescriptor(start, end);
        actual = (descriptor2 == null ? -1 : descriptor2.indentLevel);
        if (start == 0 && end == 1 && actual == 0 || start == 1 && end == 2 && actual == 1) continue;
        assertNull("Indent at lines " + start + "-" + end + " is present, but it shouldn't", descriptor2);
      }
    }
  }

  private void doTest(@NotNull String text) {
    doTest(text, fixture -> IndentModelGuidesProvider.create(fixture));
  }

  private static class IndentModelGuidesProvider implements IndentGuidesProvider {
    private final List<Guide> myGuides;

    private IndentModelGuidesProvider(List<Guide> guides) {
      myGuides = guides;
    }

    private static IndentModelGuidesProvider create(CodeInsightTestFixture fixture) {
      IndentsModel indentsModel = fixture.getEditor().getIndentsModel();
      List<Guide> guides = extractIndentGuides((IndentsModelImpl)indentsModel);
      return new IndentModelGuidesProvider(guides);
    }

    private static List<Guide> extractIndentGuides(IndentsModelImpl indentsModel) {
      return ContainerUtil.map(indentsModel.getIndents(), i -> new Guide(i.startLine, i.endLine, i.indentLevel));
    }

    @NotNull
    @Override
    public List<Guide> getGuides() {
      return myGuides;
    }
  }
}

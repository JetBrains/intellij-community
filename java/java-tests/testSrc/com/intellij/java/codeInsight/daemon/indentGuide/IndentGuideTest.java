// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.indentGuide;

import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePass;
import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePassFilter;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;
import com.intellij.openapi.editor.impl.IndentsModelImpl;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class IndentGuideTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testCodeConstructStartLine() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(myFixture.getTestName(false) + ".java", """
      class C {
        void m()\s
        {
        <caret>  int a;
        }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtilRt.EMPTY_INT_ARRAY, false);
      IndentGuideDescriptor guide = myFixture.getEditor().getIndentsModel().getCaretIndentGuide();
      assertNotNull(guide);
      assert guide.toString().equals("2 (1-2-4)");
    });
  }

  @Test
  public void testIndentModel() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(myFixture.getTestName(false) + ".java", """
      class C {
             }""");

    EdtTestUtil.runInEdtAndWait(() -> {
      IndentsModelImpl model = (IndentsModelImpl)myFixture.getEditor().getIndentsModel();
      int nLines = myFixture.getEditor().getDocument().getLineCount();
      int nCols = myFixture.getEditor().getDocument().getLineEndOffset(0);

      model.assumeIndents(new ArrayList<>());
      for (int start = 0; start <= nLines; start++) {
        for (int end = start + 1; end <= nLines; end++) {
          final IndentGuideDescriptor descriptor = model.getDescriptor(start, end);
          assertNull(descriptor, "Indent at lines " + start + "-" + end + " is present, but it shouldn't");
        }
      }

      for (int start = 0; start <= nLines; start++) {
        for (int end = start + 1; end <= nLines; end++) {
          for (int level = 0; level < nCols; level++) {
            model.assumeIndents(List.of(new IndentGuideDescriptor(level, start, end)));
            final IndentGuideDescriptor descriptor = model.getDescriptor(start, end);
            assertNotNull(descriptor, "Indent at " + level + " for lines " + start + " - " + end + " is missing");
            final int actual = descriptor.indentLevel;
            assertEquals(level, actual,
                         "Expected indent at lines " + start + " - " + end + ", level: " + level + ", found at " + actual);
          }
        }
      }

      model.assumeIndents(List.of(new IndentGuideDescriptor(0, 0, 1), new IndentGuideDescriptor(1, 1, 2)));
      final IndentGuideDescriptor descriptor = model.getDescriptor(0, 1);
      assertNotNull(descriptor, "Indent at 0 for lines 0 - 1 is missing");
      int actual = descriptor.indentLevel;
      assertEquals(0, actual, "Expected indent at lines 0 - 1, level: 0, found at " + actual);
      final IndentGuideDescriptor descriptor1 = model.getDescriptor(1, 2);
      assertNotNull(descriptor1, "Indent at 1 for lines 1 - 2 is missing");
      actual = descriptor1.indentLevel;
      assertEquals(1, actual, "Expected indent at lines 1 - 2, level: 1, found at " + actual);
      for (int start = 0; start <= nLines; start++) {
        for (int end = start + 1; end <= nLines; end++) {
          final IndentGuideDescriptor descriptor2 = model.getDescriptor(start, end);
          actual = (descriptor2 == null ? -1 : descriptor2.indentLevel);
          if (start == 0 && end == 1 && actual == 0 || start == 1 && end == 2 && actual == 1) continue;
          assertNull(descriptor2, "Indent at lines " + start + "-" + end + " is present, but it shouldn't");
        }
      }
    });
  }

  @Test
  public void testIndexNotReadyDuringCollectDoesNotThrowOnApply_Async() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(myFixture.getTestName(false) + ".java", """
      class C {
        void m() {
          if (true) {
            int a;
          }
        }
      }
      """);

    ExtensionPointName<IndentGuidePassFilter> epName = ExtensionPointName.create("com.intellij.daemon.indentGuidePassFilter");
    ExtensionTestUtil.maskExtensions(epName, List.of(new IndentGuidePassFilter() {
      @Override
      public boolean shouldRunIndentsPass(@NotNull Editor editor) {
        return true;
      }

      @Override
      public boolean shouldShowIndentGuide(@NotNull Editor editor, @NotNull IndentGuideDescriptor descriptor) {
        throw IndexNotReadyException.create();
      }
    }), myFixture.getTestRootDisposable());

    IndentGuidePass pass = ActionUtil.underModalProgress(myFixture.getProject(), "", () ->
      new IndentGuidePass(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile())
    );

    boolean indexNotReadyThrown = false;
    try {
      ReadAction.run(() -> pass.doCollectInformation(new EmptyProgressIndicator()));
    }
    catch (IndexNotReadyException ignored) {
      indexNotReadyThrown = true;
    }
    assertTrue(indexNotReadyThrown, "collect should fail with IndexNotReadyException to emulate daemon behavior");

    EdtTestUtil.runInEdtAndWait(() -> pass.doApplyInformationToEditor());

    IndentsModelImpl model = (IndentsModelImpl)myFixture.getEditor().getIndentsModel();
    assertTrue(model.getIndents().isEmpty(), "indents model should stay empty when collect fails with IndexNotReadyException");
  }

  private void doTest(@NotNull String text) {
    IndentGuideTestUtil.doTest(codeInsightFixture.get(), text, fixture -> IndentModelGuidesProvider.create(fixture));
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

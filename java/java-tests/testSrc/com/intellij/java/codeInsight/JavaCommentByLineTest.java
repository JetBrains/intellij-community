// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CommentByLineTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class JavaCommentByLineTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  private void doTest() {
    CommentByLineTestUtil.doTest(codeInsightFixture.get(), testMethodName);
  }

  private void doInvertedTest() {
    CommentByLineTestUtil.doInvertedTest(codeInsightFixture.get(), testMethodName, 1);
  }

  private static @NotNull CommonCodeStyleSettings getJavaSettings() {
    return CodeStyle.getSettings(projectFixture.get()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  private void buildInitialFoldings() {
    Editor editor = codeInsightFixture.get().getEditor();
    EdtTestUtil.runInEdtAndWait(() -> EditorTestUtil.buildInitialFoldingsInBackground(editor));
  }

  @Test
  public void testJava1() {
    doTest();
  }

  @Test
  public void testJava2() {
    doTest();
  }

  @Test
  public void testJava3() {
    doTest();
  }

  @Test
  public void testJava4() {
    getJavaSettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  @Test
  public void testJava5() {
    getJavaSettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  @Test
  public void testJava6() {

    getJavaSettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  @Test
  public void testJava_MulticaretSingleLine() {
    doTest();
  }
  @Test
  public void testJava_MulticaretBlock() {
    doTest();
  }
  @Test
  public void testJava_MulticaretUncommentSeveralBlocks() {
    doTest();
  }

  @Test
  public void testJava_AddSpace() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.LINE_COMMENT_AT_FIRST_COLUMN = false;
    settings.LINE_COMMENT_ADD_SPACE = true;
    doTest();
  }

  @Test
  public void testJava_UncommentRemovingSpace() {
    getJavaSettings().LINE_COMMENT_ADD_SPACE = true;
    doTest();
  }

  @Test
  public void testJava_CommentUncommentDoesNotAddSpacesOnEmptyLine() {
    getJavaSettings().LINE_COMMENT_AT_FIRST_COLUMN = false;
    doInvertedTest();
  }

  @Test
  public void testFoldedOneLineMethod() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(PlatformTestUtil.getTestName(testMethodName, false) + ".java",
                              """
                                class C {
                                  void m() {
                                    <caret>System.out.println();
                                  }
                                }""");
    buildInitialFoldings();
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    CommentByLineTestUtil.performAction(myFixture);
    myFixture.checkResult("""
                            class C {
                            //  void m() {
                            //    System.out.println();
                            //  }
                            }""");
  }

  @Test
  public void testFoldingAtSelectionEnd() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(PlatformTestUtil.getTestName(testMethodName, false) + ".java",
                              """
                                class C {
                                <caret>  void m1() {
                                    System.out.println();
                                  }
                                  void m2() {
                                    System.out.println();
                                  }
                                }""");
    buildInitialFoldings();
    myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    CommentByLineTestUtil.performAction(myFixture);
    myFixture.checkResult("""
                            class C {
                            //  void m1() {
                            //    System.out.println();
                            //  }
                              void m2() {
                                System.out.println();
                              }
                            }""");
  }

  @Test
  public void testJava_AlignedWithSmartTabs() {
    CommonCodeStyleSettings settings = getJavaSettings();
    settings.LINE_COMMENT_AT_FIRST_COLUMN = false;
    settings.getIndentOptions().USE_TAB_CHARACTER = true;
    settings.getIndentOptions().SMART_TABS = true;
    doTest();
  }
}

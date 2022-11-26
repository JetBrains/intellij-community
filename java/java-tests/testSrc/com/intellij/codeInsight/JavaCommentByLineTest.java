// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

public class JavaCommentByLineTest extends CommentByLineTestBase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testJava1() {
    doTest();
  }

  public void testJava2() {
    doTest();
  }

  public void testJava3() {
    doTest();
  }

  public void testJava4() {
    getLanguageSettings(JavaLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  public void testJava5() {
    getLanguageSettings(JavaLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  public void testJava6() {

    getLanguageSettings(JavaLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doTest();
  }

  public void testJava_MulticaretSingleLine() {
    doTest();
  }
  public void testJava_MulticaretBlock() {
    doTest();
  }
  public void testJava_MulticaretUncommentSeveralBlocks() {
    doTest();
  }

  public void testJava_AddSpace() {
    CommonCodeStyleSettings settings = getLanguageSettings(JavaLanguage.INSTANCE);
    settings.LINE_COMMENT_AT_FIRST_COLUMN = false;
    settings.LINE_COMMENT_ADD_SPACE = true;
    doTest();
  }

  public void testJava_UncommentRemovingSpace() {
    getLanguageSettings(JavaLanguage.INSTANCE).LINE_COMMENT_ADD_SPACE = true;
    doTest();
  }

  public void testJava_CommentUncommentDoesNotAddSpacesOnEmptyLine() {
    getLanguageSettings(JavaLanguage.INSTANCE).LINE_COMMENT_AT_FIRST_COLUMN = false;
    doInvertedTest(1);
  }

  public void testFoldedOneLineMethod() {
    configureFromFileText(getTestName(false) + ".java",
                          """
                            class C {
                              void m() {
                                <caret>System.out.println();
                              }
                            }""");
    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(getEditor());
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    performAction();
    checkResultByText("""
                        class C {
                        //  void m() {
                        //    System.out.println();
                        //  }
                        }""");
  }

  public void testFoldingAtSelectionEnd() {
    configureFromFileText(getTestName(false) + ".java",
                          """
                            class C {
                            <caret>  void m1() {
                                System.out.println();
                              }
                              void m2() {
                                System.out.println();
                              }
                            }""");
    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(getEditor());
    executeAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    performAction();
    checkResultByText("""
                        class C {
                        //  void m1() {
                        //    System.out.println();
                        //  }
                          void m2() {
                            System.out.println();
                          }
                        }""");
  }

  public void testJava_AlignedWithSmartTabs() {
    CommonCodeStyleSettings settings = getLanguageSettings(JavaLanguage.INSTANCE);
    settings.LINE_COMMENT_AT_FIRST_COLUMN = false;
    settings.getIndentOptions().USE_TAB_CHARACTER = true;
    settings.getIndentOptions().SMART_TABS = true;
    doTest();
  }

  public void testUncommentLargeFilePerformance() {
    StringBuilder source = new StringBuilder("class C {\n");
    for (int i = 0; i < 5000; i++) {
      source.append("    int value").append(i).append(";\n");
    }
    source.append("}");
    configureFromFileText(getTestName(false) + ".java", source.toString());
    executeAction(IdeActions.ACTION_SELECT_ALL);
    PlatformTestUtil.startPerformanceTest("Uncommenting large file", 2000, CommentByLineTestBase::performAction)
      .setup(CommentByLineTestBase::performAction)
      .assertTiming();
  }
}

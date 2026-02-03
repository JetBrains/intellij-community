// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class JavaCommentByBlockTest extends LightPlatformCodeInsightTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testIDEADEV27995() {
    configureByFile("/codeInsight/commentByBlock/java/beforeIdeadev27995.java");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/java/afterIdeadev27995.java");
  }

  public void testJava1() {
    configureByFile("/codeInsight/commentByBlock/java/before1.java");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/java/after1.java");
  }

  public void testJava2() {
    configureByFile("/codeInsight/commentByBlock/java/before2.java");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/java/after2.java");
  }
  public void testJava3() {
    configureByFile("/codeInsight/commentByBlock/java/before3.java");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/java/after3.java");
  }

  public void testJava4() {
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean blockCommentAtFirstCol = javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN;
    try {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = false;
      configureByFile("/codeInsight/commentByBlock/java/before4.java");
      performAction();
      checkResultByFile("/codeInsight/commentByBlock/java/after4.java");
    }
    finally {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = blockCommentAtFirstCol;
    }
  }

  public void testJava5() {
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean blockCommentAtFirstCol = javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN;
    try {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = false;
      configureByFile("/codeInsight/commentByBlock/java/before5.java");
      performAction();
      checkResultByFile("/codeInsight/commentByBlock/java/after5.java");
    }
    finally {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = blockCommentAtFirstCol;
    }
  }

  public void testMulticaret() {
    configureByFile("/codeInsight/commentByBlock/java/MulticaretBefore.java");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/java/MulticaretAfter.java");
  }

  public void testMulticaretUncomment() {
    configureByFile("/codeInsight/commentByBlock/java/MulticaretUncommentBefore.java");
    performAction();
    checkResultByFile("/codeInsight/commentByBlock/java/MulticaretUncommentAfter.java");
  }

  public void testCommentStringWithBlockPrefix() {
    doTestForText("class C { <selection>String s = \"/*\";<caret></selection> }",
                  "class C { <selection>/*String s = \"/*\";<caret>*/</selection> }");
  }

  public void testCommentStringWithBlockSuffix() {
    doTestForText("class C { <selection>String s = \"*/\";<caret></selection> }",
                  "class C { <selection>String s = \"*/\";<caret></selection> }");
  }

  public void testCommentRangeIntersectingWithExistingComment() {
    doTestForText("class C { /* some <selection>comment */ int i = 3;<caret></selection> }",
                  "class C { /* some <selection>comment */ int i = 3;<caret></selection> }");
  }

  public void testSelectionContainsJavadoc() {
    doTestForText("""
                    class C {
                        <selection>int a;
                        /** doc */
                        int b;<caret></selection>
                    }""",
                  """
                    class C {
                        <selection>/*int a;
                        *//** doc *//*
                        int b;*/<caret></selection>
                    }""");
  }

  private void doTestForText(String before, String after) {
    configureFromFileText(getTestName(true) + ".java", before);
    performAction();
    checkResultByText(after);
  }

  private void performAction() {
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_COMMENT_BLOCK);
  }
}


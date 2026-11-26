// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class JavaCommentByBlockTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testIDEADEV27995() {
    codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/beforeIdeadev27995.java");
    performAction();
    codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/afterIdeadev27995.java");
  }

  @Test
  public void testJava1() {
    codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/before1.java");
    performAction();
    codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/after1.java");
  }

  @Test
  public void testJava2() {
    codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/before2.java");
    performAction();
    codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/after2.java");
  }
  @Test
  public void testJava3() {
    codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/before3.java");
    performAction();
    codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/after3.java");
  }

  @Test
  public void testJava4() {
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(codeInsightFixture.get().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean blockCommentAtFirstCol = javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN;
    try {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = false;
      codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/before4.java");
      performAction();
      codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/after4.java");
    }
    finally {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = blockCommentAtFirstCol;
    }
  }

  @Test
  public void testJava5() {
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(codeInsightFixture.get().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean blockCommentAtFirstCol = javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN;
    try {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = false;
      codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/before5.java");
      performAction();
      codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/after5.java");
    }
    finally {
      javaSettings.BLOCK_COMMENT_AT_FIRST_COLUMN = blockCommentAtFirstCol;
    }
  }

  @Test
  public void testMulticaret() {
    codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/MulticaretBefore.java");
    performAction();
    codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/MulticaretAfter.java");
  }

  @Test
  public void testMulticaretUncomment() {
    codeInsightFixture.get().configureByFile("/codeInsight/commentByBlock/java/MulticaretUncommentBefore.java");
    performAction();
    codeInsightFixture.get().checkResultByFile("/codeInsight/commentByBlock/java/MulticaretUncommentAfter.java");
  }

  @Test
  public void testCommentStringWithBlockPrefix() {
    doTestForText("class C { <selection>String s = \"/*\";<caret></selection> }",
                  "class C { <selection>/*String s = \"/*\";<caret>*/</selection> }");
  }

  @Test
  public void testCommentStringWithBlockSuffix() {
    doTestForText("class C { <selection>String s = \"*/\";<caret></selection> }",
                  "class C { <selection>String s = \"*/\";<caret></selection> }");
  }

  @Test
  public void testCommentRangeIntersectingWithExistingComment() {
    doTestForText("class C { /* some <selection>comment */ int i = 3;<caret></selection> }",
                  "class C { /* some <selection>comment */ int i = 3;<caret></selection> }");
  }

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(myFixture.getTestName(true) + ".java", before);
    performAction();
    myFixture.checkResult(after);
  }

  private void performAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> EditorTestUtil.executeAction(myFixture.getEditor(), IdeActions.ACTION_COMMENT_BLOCK));
  }
}


// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author ignatov
 */
@TestApplication
@IndexingModeTemplate
public class ReturnPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "return");

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testComposite() {
    doTest();
  }

  @Test
  public void testComposite2() {
    doTest();
  }

  @Test
  public void testIncompleteExpression() {
    doTest();
  }

  @Test
  public void testIncompleteConstructor() {
    doTest();
  }

  @Test
  public void testIncompleteExpressionWithParam() {
    doTest();
  }

  @Test
  public void testIncompleteParentheses() {
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest());
  }
}
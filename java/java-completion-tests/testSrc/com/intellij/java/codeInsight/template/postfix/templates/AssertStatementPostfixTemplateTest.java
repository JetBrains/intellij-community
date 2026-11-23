// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
@IndexingModeTemplate
public class AssertStatementPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "assert");

  @Test
  public void testBooleanVariableBeforeAssignment() {
    doTest();
  }

  @Test
  public void testBoxedBooleanVariable() {
    doTest();
  }

  @Test
  public void testNotBooleanExpression() {
    doTest();
  }

  @Test
  public void testUnresolvedVariable() {
    doTest();
  }

  @Test
  public void testSeveralConditions() {
    doTest();
  }

  @Test
  public void testIntegerComparison() {
    doTest();
  }

  @Test
  public void testMethodInvocation() {
    doTest();
  }

  @Test
  public void testInstanceof() {
    doTest();
  }

  @Test
  public void testInstanceofBeforeReturnStatement() {
    doTest();
  }

  @Test
  public void testNotNull() {
    doTest();
  }

  @Test
  public void testBeforeAssignment() {
    doTest();
  }

  @Test
  public void testSimpleWithSemicolon() {
    doTest();
  }

  @Test
  public void testIncompleteExpression() {
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModAssertStatementPostfixTemplateTest extends AssertStatementPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}


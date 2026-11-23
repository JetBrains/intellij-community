// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
public class NotExpressionPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "not");

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testComplexCondition() {
    doTest();
  }

  @Test
  public void testBoxedBoolean() {
    doTest();
  }

  @Test
  public void testExclamation() {
    doTest();
  }

  @Test
  public void testConditionInBooleanMethodCall() {
    doTest();
  }

  @Test
  public void testSmartNegationPresentEmpty() {
    doTest();
  }

  @Test
  public void testSmartNegationNoneAny() {
    doTest();
  }
  
  @Test
  public void testAlreadyNegated() {
    doTest();
  }
  
  @Test
  public void testObjectsNonNull() {
    doTest();
  }
  
  @Test
  public void testMethodRef() {
    doTest();
  }
  
  @Test
  public void testMethodRef2Lambda() {
    doTest();
  }
  
  @Test
  public void testMethodRefOptional() {
    doTest();
  }
  
  @Test
  public void testMethodRefComplexQualifier() {
    doTest();
  }

  //  public void testNegation()          { doTest(); } // todo: test for chooser

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModNotExpressionPostfixTemplateTest extends NotExpressionPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
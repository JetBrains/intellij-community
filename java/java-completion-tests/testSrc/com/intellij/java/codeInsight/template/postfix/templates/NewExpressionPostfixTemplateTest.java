// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
@IndexingModeTemplate
public class NewExpressionPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "new");

  @Test
  public void testNew01() {
    doTest();
  }

  @Test
  public void testNew04() {
    doTest();
  }

  @Test
  public void testNew05() {
    doTest();
  }

  @Test
  public void testNew06() {
    doTest();
  }

  @Test
  public void testNew07() {
    doTest();
  }

  @Test
  public void testNew08() {
    doTest();
  }

  @Test
  public void testNewOnAssignExpression() {
    doTest();
  }

  @Test
  public void testNewInsideExpression() {
    doTest();
  }

  @Test
  public void testNewWithCall() {
    doTest();
  }

  @Test
  public void testNotClassCall() {
    doTest();
  }

  @Test
  public void testNotClassCallWithClass() {
    doTest();
  }

  @Test
  public void testNewUnresolved() {
    doTest();
  }

  @Test
  public void testNewAfterNew() {
    doTest();
  }

  @Test
  public void testNewInsideVarargCall() {
    doTest();
  }

  @Test
  public void testNewInsideVarargCall2() {
    doTest();
  }

  @NeedsIndex.Full
  @Test
  public void testNewNestedClass() {
    doTest();
  }

  @Test
  public void testNewNestedClass2() {
    doTest();
  }

  @Test
  public void testNewFQN() {
    doTest();
  }

  @Test
  public void testNewFQNConflict() {
    doTest();
  }

  @Test
  public void testNewFQNImport() {
    doTest();
  }

  @Test
  public void testNewInaccessible() {
    LightJavaCodeInsightTestFixture myFixture = postfix.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> myFixture.addClass("class Test {private Test() {}}"));
    doTest();
  }
  
  @NeedsIndex.ForStandardLibrary
  @Test
  public void testNewNonImported() {
    doTest();
  }
  
  @Test
  public void testNewNonExisting() {
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModNewExpressionPostfixTemplateTest extends NewExpressionPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

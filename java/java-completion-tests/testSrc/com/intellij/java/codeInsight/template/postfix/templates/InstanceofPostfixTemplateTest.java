// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModeTemplate
public class InstanceofPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "instanceof");

  @Test
  public void testSingleExpression() {
    doTest();
  }

  @Test
  public void testAlias() {
    doTest();
  }

  @Test
  public void testPrimitive() {
    doTest();
  }

  @Test
  public void testSingleExpressionTemplate() {
    LightJavaCodeInsightTestFixture myFixture = postfix.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());

      myFixture.configureByFile(myFixture.getTestName(true) + ".java");
      myFixture.type('\t');

      TemplateState templateState = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      assertNotNull(templateState);
      assertFalse(templateState.isFinished());

      myFixture.type("Integer");
      templateState.nextTab();
      assertTrue(templateState.isFinished());

      myFixture.checkResultByFile(myFixture.getTestName(true) + "_after.java", true);
    });
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModInstanceofPostfixTemplateTest extends InstanceofPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

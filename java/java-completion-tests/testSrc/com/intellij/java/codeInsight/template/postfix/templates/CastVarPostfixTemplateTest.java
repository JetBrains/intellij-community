// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.JavaRefactoringSettings;
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
public class CastVarPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "castvar");

  @Test
  public void testSingleExpression() {
    doTest();
  }

  @Test
  public void testAssigned() {
    doTest();
  }

  @Test
  public void testTypeAnnotations() {
    doTest();
  }

  @Test
  public void testFinalSingleExpression() {
    JavaCodeStyleSettings customSettings = JavaCodeStyleSettings.getInstance(postfix.get().getFixture().getProject());
    customSettings.GENERATE_FINAL_LOCALS = true;

    doTest();
  }

  @Test
  public void testVarSingleExpression() {
    JavaRefactoringSettings instance = JavaRefactoringSettings.getInstance();
    instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
    try {
      doTest();
    }
    finally {
      instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
    }
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModCastVarPostfixTemplateTest extends CastVarPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

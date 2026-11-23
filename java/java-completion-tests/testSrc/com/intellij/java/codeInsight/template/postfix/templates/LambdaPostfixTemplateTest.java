// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
@IndexingModeTemplate
public class LambdaPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "lambda");

  private LanguageLevel myDefaultLanguageLevel;

  @BeforeEach
  void setUp() {
    Project project = postfix.get().getFixture().getProject();
    EdtTestUtil.runInEdtAndWait(() -> myDefaultLanguageLevel = IdeaTestUtil.setProjectLanguageLevel(project, LanguageLevel.JDK_1_8));
  }

  @AfterEach
  void tearDown() {
    Project project = postfix.get().getFixture().getProject();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.setProjectLanguageLevel(project, myDefaultLanguageLevel));
    myDefaultLanguageLevel = null;
  }

  @Test
  public void testSimple() {
    doTest();
  }
  
  @Test
  public void testDoNotExpandOnJavaLess8() {
    LightJavaCodeInsightTestFixture myFixture = postfix.get().getFixture();
    EdtTestUtil.runInEdtAndWait(
      () -> IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_6, myFixture.getTestRootDisposable()));
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModLambdaPostfixTemplateTest extends LambdaPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}


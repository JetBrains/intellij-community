// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
@IndexingModeTemplate
public class StreamPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "stream");

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testExpressionContext() {
    doTest();
  }
  
  @Test
  public void testInLambda() {
    if (DumbService.isDumb(postfix.get().getFixture().getProject()) &&
        !Registry.is("ide.dumb.mode.check.awareness")) {
      // See IDEA-362230
      return;
    }
    doTest();
  }

  @Test
  public void testAssignment() {
    doTest();
  }

  @Test
  public void testNotAvailable() {
    doTest();
  }

  @Test
  public void testDoNotExpandOnJavaLess8() {
    Module module = postfix.get().getFixture().getModule();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_6, this::doTest));
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModStreamPostfixTemplateTest extends StreamPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}


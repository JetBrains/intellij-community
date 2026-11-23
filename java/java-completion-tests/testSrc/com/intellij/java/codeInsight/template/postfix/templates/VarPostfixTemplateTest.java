// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author ignatov
 */
@TestApplication
@IndexingModeTemplate
public class VarPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "var");

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testAdd() {
    doTest();
  }

  @Test
  public void testStreamStep() {
    if (useModCommandTemplates()) return;
    UiInterceptors.register(new ChooserInterceptor(List.of("Create variable inside current lambda", "Extract as 'map' operation"),
                                                   "Create variable inside current lambda"));
    doTest();
  }

  @Test
  public void testStreamStep2() {
    if (useModCommandTemplates()) return;
    UiInterceptors.register(new ChooserInterceptor(List.of("Create variable inside current lambda", "Extract as 'map' operation"),
                                                   "Extract as 'map' operation"));
    doTest();
  }

  @Test
  public void testAnonymous() {
    doTest();
  }

  protected void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModVarPostfixTemplateTest extends VarPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }

    @NeedsIndex.SmartMode(reason = "Requires resolving")
    @Override
    @Test
    public void testStreamStep() {
      doTest();
    }
  }
}

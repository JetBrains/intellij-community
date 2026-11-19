// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author ignatov
 */
@TestApplication
@IndexingModeTemplate
public class SwitchPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "switch");

  @BeforeEach
  void setUp() {
    Project project = postfix.get().getFixture().getProject();
    EdtTestUtil.runInEdtAndWait(() -> IdeaTestUtil.setProjectLanguageLevel(project, LanguageLevel.JDK_1_7));
  }

  @Test
  public void testInt() {
    doTest();
  }

  @Test
  public void testByte() {
    doTest();
  }

  @Test
  public void testChar() {
    doTest();
  }

  @Test
  public void testShort() {
    doTest();
  }

  @Test
  public void testEnum() {
    doTest();
  }

  @Test
  public void testString() {
    doTest();
  }

  @Test
  public void testComposite() {
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModSwitchPostfixTemplateTest extends SwitchPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
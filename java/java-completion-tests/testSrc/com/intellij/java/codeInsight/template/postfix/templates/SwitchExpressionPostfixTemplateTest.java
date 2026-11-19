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
public class SwitchExpressionPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "switch");

  @Test
  public void testIntExprInit() {
    doTest();
  }

  @Test
  public void testIntExprAssign() {
    doTest();
  }

  @Test
  public void testByteExprInit() {
    doTest();
  }

  @Test
  public void testByteExprAssign() {
    doTest();
  }

  @Test
  public void testCharExprReturn() {
    doTest();
  }

  @Test
  public void testCharExprArg() {
    doTest();
  }

  @Test
  public void testShortExprReturn() {
    doTest();
  }

  @Test
  public void testShortExprArg() {
    doTest();
  }

  @Test
  public void testEnumExprInit() {
    doTest();
  }

  @Test
  public void testEnumExprAssign() {
    doTest();
  }

  @Test
  public void testStringExprInit() {
    doTest();
  }

  @Test
  public void testStringExprAssign() {
    doTest();
  }

  @Test
  public void testCompositeExprInit() {
    doTest();
  }

  @Test
  public void testCompositeExprAssign() {
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> postfix.get().doTest(useModCommandTemplates()));
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModSwitchExpressionPostfixTemplateTest extends SwitchExpressionPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
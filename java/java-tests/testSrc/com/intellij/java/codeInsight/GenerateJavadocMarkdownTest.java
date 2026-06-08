// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import kotlin.Unit;

/// Variant of [GenerateJavadocTest] to handle Markdown comment generation
/// Since the workflow for Markdown comments is different ([com.intellij.codeInsight.completion.CompletionContributor]-based),
/// it is a separate class
@HeavyPlatformTestCase.WrapInCommand
public class GenerateJavadocMarkdownTest extends LightFixtureCompletionTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/generateJavadocMarkdown/";
  }

  public void testClass() { doTestWithTemplates(); }
  public void testClassParam() { doTestWithTemplates(); }
  public void testMethodEmpty() { doTestWithTemplates(); }
  public void testMethodParam() { doTestWithTemplates(); }
  public void testMethodReturn() { doTestWithTemplates(); }
  public void testMethodThrows() { doTestWithTemplates(); }
  public void testMethodFull() { doTestWithTemplates(); }
  public void testMethodAbstract() { doTestWithTemplates(); }

  public void testGenerationDisabled01() { doTestWithTemplates(); }
  public void testGenerationDisabled02() { doTestWithTemplates(); }
  public void testGenerationDisabled03() { doTestWithTemplates(); }
  public void testDanglingDoc() { doTestWithTemplates(); }
  public void testNoClassCastException() { doTestWithTemplates(); }

  /// Sanity check: verify template-less code path doesn't explode
  public void testMethodFullNoTemplate() { doTestNoTemplates(); }

  private void doTestNoTemplates() {
    RegistryTestUtil.withValue(Registry.get("java.javadoc.use.templates"), Boolean.FALSE, ()-> {
      doTest();
      return Unit.INSTANCE;
    });
  }

  private void doTestWithTemplates() {
    RegistryTestUtil.withValue(Registry.get("java.javadoc.use.templates"), Boolean.TRUE, ()-> {
      doTest();
      return Unit.INSTANCE;
    });
  }

  private void doTest() {
    String name = getTestName(true);
    configureByFile(name + ".before.java");
    performAction();
    checkResultByFile(name + ".after.java");
  }
  
  private void performAction() {
    myFixture.completeBasic();
    if (myItems.length <= 1)
      myFixture.type("\t");
  }
}

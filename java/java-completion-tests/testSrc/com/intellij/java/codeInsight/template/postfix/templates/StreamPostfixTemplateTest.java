// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class StreamPostfixTemplateTest extends PostfixTemplateTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "stream";
  }

  public void testSimple() {
    doTest();
  }

  public void testExpressionContext() {
    doTest();
  }
  
  public void testInLambda() {
    if (DumbService.isDumb(myFixture.getProject()) &&
        !Registry.is("ide.dumb.mode.check.awareness")) {
      // See IDEA-362230
      return;
    }
    doTest();
  }

  public void testAssignment() {
    doTest();
  }

  public void testNotAvailable() {
    doTest();
  }

  public void testDoNotExpandOnJavaLess8() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  public static class ModStreamPostfixTemplateTest extends StreamPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}


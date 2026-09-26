// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class TryWithResourcesPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "twr";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
  }

  public void testSimple() {
    doTest();
  }

  public void testSimpleWithMyException() {
    doTest();
  }

  public void testSimpleWithConflict() {
    doTest();
  }

  public void testSimpleNotAutoCloseable() {
    doTest();
  }

  public static class ModTryWithResourcesPostfixTemplateTest extends TryWithResourcesPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

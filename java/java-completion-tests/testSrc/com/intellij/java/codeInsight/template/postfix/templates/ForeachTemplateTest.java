// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class ForeachTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "for";
  }

  public void testInts() {
    doTest();
  }

  public void testBeforeAssignment() {
    doTest();
  }

  public void testInAnonymousRunnable() {
    doTest();
  }
  
  public void testIterSameAsFor() {
    doTest();
  }
  
  public void testNoDoubleNotNull() {
    doTest();
  }
  
  public void testNoDoubleNotNullNested() {
    doTest();
  }

  public void testFinalLocals() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.GENERATE_FINAL_LOCALS = true;
    doTest();
  }

  public static class ModForeachTemplateTest extends ForeachTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

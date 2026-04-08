// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ArgumentPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testSimple() {
    doTest();
  }
  
  public void testLocalVariable() {
    doTest();
  }

  public void testInStatement() {
    doTest();
  }

  @Override
  protected String textCall() {
    return "\tfunctionCall\t";
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "arg";
  }

  public static class ModArgumentPostfixTemplateTest extends ArgumentPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

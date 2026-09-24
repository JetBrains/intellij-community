// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class SynchronizedPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "synchronized";
  }

  public void testObject() {
    doTest();
  }

  public static class ModSynchronizedPostfixTemplateTest extends SynchronizedPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
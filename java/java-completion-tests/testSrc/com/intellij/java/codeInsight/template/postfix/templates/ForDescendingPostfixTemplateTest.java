// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.refactoring.JavaRefactoringSettings;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

public class ForDescendingPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "forr";
  }

  public void testIntArray() {
    doTest();
  }
  
  public void testIntArrayVar() {
    JavaRefactoringSettings instance = JavaRefactoringSettings.getInstance();
    instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = true;
    try {
      doTest();
    }
    finally {
      instance.INTRODUCE_LOCAL_CREATE_VAR_TYPE = false;
    }
  }

  public void testByteNumber() {
    doTest();
  }

  public void testBoxedIntegerArray() {
    doTest();
  }

  public void testBoxedLongArray() {
    doTest();
  }

  @Ignore("AT-4013")
  public static class ModForDescendingPostfixTemplateTest extends ForDescendingPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class IopPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "iop";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_25;
  }

  public void testSimple() {
    doTest();
  }

  public void testVoid() {
    doTest();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  public static class ModIopPostfixTemplateTest extends IopPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }

  public static class BeforeJdk25IopPostfixTemplateTest extends PostfixTemplateTestCase {
    @NotNull
    @Override
    protected String getSuffix() {
      return "iop";
    }

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
      return JAVA_21;
    }

    public void testNotAvailableBeforeJdk25() {
      doTest();
    }
  }
}

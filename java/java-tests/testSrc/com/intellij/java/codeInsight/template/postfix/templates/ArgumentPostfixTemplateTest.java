// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import org.jetbrains.annotations.NotNull;

public class ArgumentPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testSimple() {
    doArgTest();
  }
  
  public void testLocalVariable() {
    doArgTest();
  }

  public void testInStatement() {
    doArgTest();
  }

  private void doArgTest() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    myFixture.configureByFile(getTestName(true) + ".java");
    myFixture.type("\tfunctionCall\t");
    myFixture.checkResultByFile(getTestName(true) + "_after.java", true);
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "arg";
  }
}

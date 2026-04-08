// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.idea.IJIgnore;
import org.jetbrains.annotations.NotNull;

public class SoutPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "sout";
  }

  @IJIgnore(issue = "AT-4013")
  public void testSimple() {
    doTest();
  }

  @IJIgnore(issue = "AT-4013")
  public void testSerr() {
    doTest();
  }

  @IJIgnore(issue = "AT-4013")
  public void testSouf() {
    doTest();
  }

  public void testVoid() {
    doTest();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  @IJIgnore(issue = "AT-4013")
  public void testWithComment() {
    doTest();
  }

  public static class ModSoutPostfixTemplateTest extends SoutPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
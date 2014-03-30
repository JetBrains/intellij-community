package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class VarPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "var"; }

  public void testSimple() { doTest(); }
  public void testAdd()  { doTest(); }
}

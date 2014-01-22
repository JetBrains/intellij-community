package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class CastPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "cast"; }

  public void testSingleExpression() { doTest(); } // jdk mock needed
  public void testVoidExpression()   { doTest(); }
}

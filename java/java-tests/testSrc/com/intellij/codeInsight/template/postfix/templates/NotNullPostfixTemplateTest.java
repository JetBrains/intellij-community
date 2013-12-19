package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class NotNullPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "notnull"; }

  public void testSimple()            { doTest(); }
  public void testNn()                { doTest(); }
  public void testSecondStatement()   { doTest(); }
}
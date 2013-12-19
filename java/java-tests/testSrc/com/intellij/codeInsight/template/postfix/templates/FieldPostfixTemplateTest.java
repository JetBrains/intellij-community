package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class FieldPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testSimple() { doTest(); }
  public void testFoo()    { doTest(); }

  @NotNull
  @Override
  protected String getSuffix() { return "field"; }
}

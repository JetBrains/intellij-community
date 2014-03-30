package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class ParPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "par"; }

  public void testSimple() { doTest(); }
  public void testExtra()  { doTest(); }
}

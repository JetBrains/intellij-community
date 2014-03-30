package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class ReturnPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "return"; }

  public void testSimple()     { doTest(); }
  public void testComposite()  { doTest(); }
  public void testComposite2() { doTest(); }
}
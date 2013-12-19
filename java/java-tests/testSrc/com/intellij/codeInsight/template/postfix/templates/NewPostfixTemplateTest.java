package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class NewPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "new"; }

  public void testNew01() { doTest(); }
  public void testNew02() { doTest(); }
  public void testNew03() { doTest(); }
  public void testNew04() { doTest(); }
  public void testNew05() { doTest(); }
  public void testNew06() { doTest(); }
  public void testNew07() { doTest(); }
  public void testNew08() { doTest(); }
}

package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class SwitchPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "switch"; }

  public void testInt()       { doTest(); }
  public void testByte()      { doTest(); }
  public void testChar()      { doTest(); }
  public void testShort()     { doTest(); }
  public void testEnum()      { doTest(); }
  public void testString()    { doTest(); }
  public void testComposite() { doTest(); }
}
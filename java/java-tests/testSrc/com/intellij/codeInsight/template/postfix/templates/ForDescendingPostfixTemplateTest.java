package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ForDescendingPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "forr"; }

  public void testIntArray()          { doTest(); }
  public void testByteNumber()        { doTest(); }
  public void testBoxedIntegerArray() { doTest(); }
  public void testBoxedLongArray()    { doTest(); }
}

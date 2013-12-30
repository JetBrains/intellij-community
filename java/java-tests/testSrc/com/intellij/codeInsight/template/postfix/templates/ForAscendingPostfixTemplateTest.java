package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ForAscendingPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "fori"; }

  public void testIntArray() { doTest(); }
  public void testIntNumber() { doTest(); }
  public void testByteNumber() { doTest(); }
  public void testBoxedByteNumber() { doTest(); }
  public void testCollection() { doTest(); }
  public void testBoxedIntegerArray() { doTest(); }
  public void testBoxedLongArray() { doTest(); }
}

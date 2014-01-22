package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class NotExpressionPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "not"; }

  public void testSimple()            { doTest(); }
  public void testComplexCondition()  { doTest(); }
  public void testBoxedBoolean()      { doTest(); }
  public void testExclamation()       { doTest(); }
//  public void testNegation()          { doTest(); } // todo: test for chooser
}
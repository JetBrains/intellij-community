// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class NotExpressionPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "not";
  }

  public void testSimple() {
    doTest();
  }

  public void testComplexCondition() {
    doTest();
  }

  public void testBoxedBoolean() {
    doTest();
  }

  public void testExclamation() {
    doTest();
  }

  public void testConditionInBooleanMethodCall() {
    doTest();
  }

  public void testSmartNegationPresentEmpty() {
    doTest();
  }

  public void testSmartNegationNoneAny() {
    doTest();
  }
  
  public void testAlreadyNegated() {
    doTest();
  }
  
  public void testObjectsNonNull() {
    doTest();
  }
  
  public void testMethodRef() {
    doTest();
  }
  
  public void testMethodRef2Lambda() {
    doTest();
  }
  
  public void testMethodRefOptional() {
    doTest();
  }
  
  public void testMethodRefComplexQualifier() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_11;
  }

  //  public void testNegation()          { doTest(); } // todo: test for chooser

  public static class ModNotExpressionPostfixTemplateTest extends NotExpressionPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.MultipleVariablesInDeclarationInspection;

/**
 * @author Bas Leijdekkers
 */
public class MultipleVariablesInDeclarationFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new MultipleVariablesInDeclarationInspection());
    myRelativePath = "style/multiple_declaration";
    myDefaultHint = InspectionGadgetsBundle.message("normalize.declaration.quickfix");
  }

  public void testLocalVariable() { doTest(); }
  public void testField() { doTest(); }
  public void testMultipleDeclarationsLocalWithComments() { doTest(); }
  public void testMutuallyDependentForDeclarations() { doTest(); }
  public void testOptimizedForLoop() { doTest(); }
  public void testAnnotatedArrayField() {
    doTest();
  }
  public void testAnnotatedArrayLocalVariable() {
    doTest();
  }
  public void testAnnotatedVariableInForLoop() {
    doTest();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      """
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
public @interface Required {
}""",

      """
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
public @interface Preliminary {
}"""
    };
  }
}

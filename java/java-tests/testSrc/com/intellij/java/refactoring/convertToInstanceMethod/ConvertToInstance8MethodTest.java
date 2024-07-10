// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.convertToInstanceMethod;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;

public class ConvertToInstance8MethodTest extends ConvertToInstanceMethodTest {
  @Override
  protected String getBasePath() {
    return "/refactoring/convertToInstance8Method/";
  }

  public void testConflictingMembers() { doTest(0); }
  public void testNoConflictingMembers() { doTest(0); }
  public void testNoConflictingMembers2() { doTest(0); }
  public void testThisInsteadOfNoQualifier() { doTest(0); }
  public void testMethodReferenceAcceptableBySecondSearch() { doTest(0); }
  public void testConvertToInstanceMethodOfTheSameClass() { doTest(0); }
  public void testReassignedParameter() { doTest(0); }
  public void testStaticMethodOfInterfaceWithNonAccessibleInheritor() { doTest(0, null, "I i", "this / new I()"); }
  public void testEnum() { doTest(0, null, "E e"); }
  public void testAnonymousClass() { doTest(0, null, "X x"); }
  public void testInnerClass() { doTest(0, null, "X x"); }
  public void testNestedClass() { doTest(1, null, "X x", "this / new Nested()"); }
  
  public void testImplicitClass() {
    try {
      doTestException();
      fail();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(JavaRefactoringBundle.message("convertToInstanceMethod.no.parameters.with.reference.type"), e.getMessage());
    }
  }

  public void testConvertToInstanceMethodOfTheSameClassWithTypeParams() {
    try {
      doTest(0);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(StringUtil.trimEnd(StringUtil.repeat("Impossible to infer class type arguments. When proceed, raw Bar would be created\n", 3), "\n"), e.getMessage());
    }
  }

  public void testMethodReferenceToLambda() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(0));
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.convertToInstanceMethod;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;

public class ConvertToInstance8MethodTest extends ConvertToInstanceMethodTest {
  @Override
  protected String getBasePath() {
    return "/refactoring/convertToInstance8Method/";
  }

  public void testConflictingMembers() { doTest(0); }
  public void testNoConflictingMembers() { doTest(0); }
  public void testNoConflictingMembers2() { doTest(0); }

  public void testThisInsteadOfNoQualifier() {
    doTest(0);
  }

  public void testMethodReferenceAcceptableBySecondSearch() {
    doTest(0);
  }

  public void testConvertToInstanceMethodOfTheSameClass() {
    doTest(-1);
  }

  public void testStaticMethodOfInterfaceWithNonAccessibleInheritor() {
    doTest(0);
  }

  public void testConvertToInstanceMethodOfTheSameClassWithTypeParams() {
    try {
      doTest(-1);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(StringUtil.trimEnd(StringUtil.repeat("Impossible to infer class type arguments. When proceed, raw Bar would be created\n", 3), "\n"), e.getMessage());
    }
  }

  public void testMethodReferenceToLambda() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(1));
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

}

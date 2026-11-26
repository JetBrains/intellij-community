// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.convertToInstanceMethod;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ConvertToInstance8MethodTest extends ConvertToInstanceMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_8));

  @Override
  protected TestFixture<LightJavaProject> getProjectFixture() {
    return projectFixture;
  }

  @Override
  protected String getBasePath() {
    return "/refactoring/convertToInstance8Method/";
  }

  @Test
  public void testConflictingMembers() { doTest(0); }
  @Test
  public void testNoConflictingMembers() { doTest(0); }
  @Test
  public void testNoConflictingMembers2() { doTest(0); }
  @Test
  public void testThisInsteadOfNoQualifier() { doTest(0); }
  @Test
  public void testMethodReferenceAcceptableBySecondSearch() { doTest(0); }
  @Test
  public void testConvertToInstanceMethodOfTheSameClass() { doTest(0); }
  @Test
  public void testReassignedParameter() { doTest(0); }
  //todo temporary committed to check IDEA-366687
  //public void testStaticMethodOfInterfaceWithNonAccessibleInheritor() { doTest(0, null, "I i", "this / new I()"); }
  @Test
  public void testEnum() { doTest(0, null, "E e"); }
  @Test
  public void testAnonymousClass() { doTest(0, null, "X x"); }
  @Test
  public void testInnerClass() { doTest(0, null, "X x"); }
  @Test
  public void testNestedClass() { doTest(1, null, "X x", "this / new Nested()"); }
  
  @Test
  public void testImplicitClass() {
    try {
      doTestException();
      fail();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(JavaRefactoringBundle.message("convertToInstanceMethod.no.parameters.with.reference.type"), e.getMessage());
    }
  }

  @Test
  public void testConvertToInstanceMethodOfTheSameClassWithTypeParams() {
    try {
      doTest(0);
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(StringUtil.trimEnd(StringUtil.repeat("Impossible to infer class type arguments. When proceed, raw Bar would be created\n", 3), "\n"), e.getMessage());
    }
  }

  @Test
  public void testMethodReferenceToLambda() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(0));
  }
}

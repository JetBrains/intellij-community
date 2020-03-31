// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class UnusedDeclarationTest extends AbstractUnusedDeclarationTest {

  public void testSCR6067() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testSingleton() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testSCR9690() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testFormUsage() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testSerializable() {
    doTest();
  }

  public void testPackageLocal() {
    doTest();
  }

  public void testDefaultConstructor() {
    doTest();
  }

  public void testReachableFromMain() {
    myTool.ADD_MAINS_TO_ENTRIES = true;
    doTest();
  }

  public void testMutableCalls() {
    doTest();
  }

  public void testStaticMethods() {
    doTest();
  }

  public void testAnnotationUsedInPackageInfo() {
    doTest();
  }

  public void testSuppress() {
    doTest5();
  }

  public void testSuppress1() {
    doTest5();
  }

  public void testSuppress2() {
    doTest5();
  }

  public void testChainOfSuppressions() {
    doTest5();
  }

  public void testSuppressByNoinspectionTag() {
    doTest();
  }

  public void testReachableFromXml() {
    doTest();
  }

  public void testChainOfCalls() {
    doTest();
  }

  public void testReachableFromFieldInitializer() {
    doTest();
  }

  public void testReachableFromFieldArrayInitializer() {
    doTest();
  }

  public void testConstructorReachableFromFieldInitializer() {
    doTest();
  }

  public void testAdditionalAnnotations() {
    final String testAnnotation = "Annotated";
    EntryPointsManagerBase.getInstance(getProject()).ADDITIONAL_ANNOTATIONS.add(testAnnotation);
    try {
      doTest();
    }
    finally {
      EntryPointsManagerBase.getInstance(getProject()).ADDITIONAL_ANNOTATIONS.remove(testAnnotation);
    }
  }

  public void testAccessibleFromEntryPoint() {
    EntryPointsManagerBase.ClassPattern pattern = new EntryPointsManagerBase.ClassPattern();
    pattern.pattern = "Foo";
    pattern.method = "entry";
    EntryPointsManagerBase.getInstance(getProject()).getPatterns().add(pattern);
    try {
      doTest();
    }
    finally {
      EntryPointsManagerBase.getInstance(getProject()).getPatterns().clear();
    }
  }

  public void testAnnotationInterface() {
    doTest5();
  }

  public void testUnusedEnum() {
    doTest5();
  }

  public void testNonUtility() {
    doTest();
  }

  public void testJunitEntryPoint() {
    doTest();
  }

  public void testJunitAbstractClassWithInheritor() {
    doTest();
  }

  public void testJunitAbstractClassWithoutInheritor() {
    doTest();
  }

  public void testJunitEntryPointCustomRunWith() {
    doTest();
  }

  public void testMockedField() {
    doTest();
  }

  public void testConstructorCalls() {
    doTest();
  }

  public void testConstructorCalls1() {
    doTest();
  }

  public void testNonJavaReferences() {
    doTest();
  }

  public void testEnumInstantiation() {
    doTest();
  }

  public void testEnumValues() {
    doTest();
  }

  public void testUsagesInAnonymous() {
    doTest();
  }

  public void testAbstractClassWithSerializableSubclasses() {
    doTest();
  }

  public void testClassLiteralRef() {
    doTest();
  }

  public void testClassLiteralRef2() {
    doTest();
  }

  public void testFunctionalExpressions() {
    doTest();
  }

  public void testClassUsedInMethodParameter() {
    doTest();
  }

  public void testDeprecatedAsEntryPoint() {
    doTest();
  }

  public void testReferenceFromReflection() {
    doTest();
  }

  public void testReferenceFromGroovy() {
    doTest();
  }

  public void testStaticMethodReferenceFromGroovy() {
    doTest();
  }

  public void testStaticFieldReferenceFromExternalGroovy() {
    doTest();
  }

  public void testFieldReferenceFromExternalGroovy() {
    doTest();
  }

  public void testStaticImport() {
    doTest();
  }

  public void testInstanceOf() {
    doTest();
  }

  public void testReferenceParameterList() {
    doTest();
  }

  public void testEnumConstructor() {
    doTest();
  }

  public void testTypeParameterUsed() {
    doTest();
  }

  public void testMethodCallQualifiedWithSuper() {
    doTest();
  }

  public void testMethodParametersIfMethodReferenceUsed() {
    doTest();
  }

  private void doTest5() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_5, () -> doTest());
  }
}

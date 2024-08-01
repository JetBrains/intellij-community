// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

import java.util.List;

public class UnusedDeclarationInspectionTest extends AbstractUnusedDeclarationTest {

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

  public void testPrivateMain() {
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

  public void testSuppressionIsNotAnEntryPoint() {
    doTest();
  }

  public void testSuppressByNoinspectionTag() {
    doTest();
  }

  public void testSuppressOverriddenMethod() {
    doTest();
  }

  public void testSuppressReachableOverriddenMethod() {
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
    final List<String> testAnnotations = List.of("Annotated", "RequestMapping", "Ann1");
    EntryPointsManagerBase.getInstance(getProject()).ADDITIONAL_ANNOTATIONS.addAll(testAnnotations);
    try {
      doTest();
    }
    finally {
      EntryPointsManagerBase.getInstance(getProject()).ADDITIONAL_ANNOTATIONS.removeAll(testAnnotations);
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

  public void testAnnotation() {
    doTest();
  }

  public void testAnnotationInitializedByInheritedClassReference() {
    doTest();
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
  
  public void testEnumValueOf() {
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

  public void testRecords() {
    doTest();
  }

  public void testUtilityClass() {
    doTest();
  }

  public void testJunitMethodSource() {doTest();}

  public void testImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      doTest();
    });
  }

  public void testBrokenClassToImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      doTest();
    });
  }

  private void doTest5() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_5, () -> doTest());
  }
}

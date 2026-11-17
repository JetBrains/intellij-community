// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.java.codeInspection.UnusedDeclarationFixtureKt.unusedDeclarationFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class UnusedDeclarationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<UnusedDeclarationFixture> unusedDeclaration = unusedDeclarationFixture(projectFixture);


  @Test
  public void testSCR6067() {
    unusedDeclaration.get().getTool().ADD_NONJAVA_TO_ENTRIES = false;
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testSingleton() {
    unusedDeclaration.get().getTool().ADD_NONJAVA_TO_ENTRIES = false;
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testSCR9690() {
    unusedDeclaration.get().getTool().ADD_NONJAVA_TO_ENTRIES = false;
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testFormUsage() {
    unusedDeclaration.get().getTool().ADD_NONJAVA_TO_ENTRIES = false;
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testSerializable() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testPackageLocal() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testDefaultConstructor() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReachableFromMain() {
    unusedDeclaration.get().getTool().ADD_MAINS_TO_ENTRIES = true;
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testPrivateMain() {
    unusedDeclaration.get().getTool().ADD_MAINS_TO_ENTRIES = true;
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testMutableCalls() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testStaticMethods() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testAnnotationUsedInPackageInfo() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testSuppress() {
    doTest5();
  }

  @Test
  public void testSuppress1() {
    doTest5();
  }

  @Test
  public void testSuppress2() {
    doTest5();
  }

  @Test
  public void testChainOfSuppressions() {
    doTest5();
  }

  @Test
  public void testSuppressionIsNotAnEntryPoint() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testSuppressByNoinspectionTag() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testSuppressOverriddenMethod() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testSuppressReachableOverriddenMethod() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReachableFromXml() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testChainOfCalls() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReachableFromFieldInitializer() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReachableFromFieldArrayInitializer() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testConstructorReachableFromFieldInitializer() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testAdditionalAnnotations() {
    final List<String> testAnnotations = List.of("Annotated", "RequestMapping", "Ann1");
    EntryPointsManagerBase.getInstance(unusedDeclaration.get().getInspection().getFixture().getProject()).ADDITIONAL_ANNOTATIONS.addAll(testAnnotations);
    try {
      unusedDeclaration.get().doTest();
    }
    finally {
      EntryPointsManagerBase.getInstance(unusedDeclaration.get().getInspection().getFixture().getProject()).ADDITIONAL_ANNOTATIONS.removeAll(testAnnotations);
    }
  }

  @Test
  public void testAccessibleFromEntryPoint() {
    EntryPointsManagerBase.ClassPattern pattern = new EntryPointsManagerBase.ClassPattern();
    pattern.pattern = "Foo";
    pattern.method = "entry";
    EntryPointsManagerBase.getInstance(unusedDeclaration.get().getInspection().getFixture().getProject()).getPatterns().add(pattern);
    try {
      unusedDeclaration.get().doTest();
    }
    finally {
      EntryPointsManagerBase.getInstance(unusedDeclaration.get().getInspection().getFixture().getProject()).getPatterns().clear();
    }
  }

  @Test
  public void testAnnotation() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testAnnotationInitializedByInheritedClassReference() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testUnusedEnum() {
    doTest5();
  }

  @Test
  public void testNonUtility() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testJunitEntryPoint() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testJunitAbstractClassWithInheritor() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testJunitAbstractClassWithoutInheritor() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testJunitEntryPointCustomRunWith() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testConstructorCalls() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testConstructorCalls1() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testNonJavaReferences() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testEnumInstantiation() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testEnumValues() {
    unusedDeclaration.get().doTest();
  }
  
  @Test
  public void testEnumValueOf() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testUsagesInAnonymous() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testAbstractClassWithSerializableSubclasses() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testClassLiteralRef() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testClassLiteralRef2() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testFunctionalExpressions() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testClassUsedInMethodParameter() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testDeprecatedAsEntryPoint() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReferenceFromReflection() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReferenceFromGroovy() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testStaticMethodReferenceFromGroovy() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testStaticFieldReferenceFromExternalGroovy() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testFieldReferenceFromExternalGroovy() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testStaticImport() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testInstanceOf() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testReferenceParameterList() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testEnumConstructor() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testTypeParameterUsed() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testMethodCallQualifiedWithSuper() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testMethodParametersIfMethodReferenceUsed() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testRecords() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testUtilityClass() {
    unusedDeclaration.get().doTest();
  }

  @Test
  public void testJunitMethodSource() {unusedDeclaration.get().doTest();}

  @Test
  public void testImplicitClass() {
    IdeaTestUtil.withLevel(unusedDeclaration.get().getInspection().getFixture().getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      unusedDeclaration.get().doTest();
    });
  }

  @Test
  public void testUnusedGuardStatement() {
    IdeaTestUtil.withLevel(unusedDeclaration.get().getInspection().getFixture().getModule(), JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS.getMinimumLevel(), () -> {
      unusedDeclaration.get().doTest();
    });
  }

  @Test
  public void testBrokenClassToImplicitClass() {
    IdeaTestUtil.withLevel(unusedDeclaration.get().getInspection().getFixture().getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), () -> {
      unusedDeclaration.get().doTest();
    });
  }

  @Test
  public void testSeveralMainMethods() {
    IdeaTestUtil.withLevel(unusedDeclaration.get().getInspection().getFixture().getModule(), LanguageLevel.JDK_25, () -> {
      unusedDeclaration.get().getTool().ADD_MAINS_TO_ENTRIES = true;
      unusedDeclaration.get().doTest();
    });
  }

  private void doTest5() {
    IdeaTestUtil.withLevel(unusedDeclaration.get().getInspection().getFixture().getModule(), LanguageLevel.JDK_1_5, () -> unusedDeclaration.get().doTest());
  }
}

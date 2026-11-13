// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.idea.TestFor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addJSpecifyNonNull;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addJSpecifyNullMarked;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.setupTypeUseAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowInspection21Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testSuspiciousLabelElementsJava19() {
    dataFlow.get().doTest();
  }

  @Test
  public void testParameterNullabilityFromSwitch() {
    dataFlow.get().doTest();
  }

  @Test
  public void testDefaultLabelElementInSwitch() {
    dataFlow.get().doTest();
  }

  @Test
  public void testSuspiciousLabelElements() {
    dataFlow.get().doTest();
  }

  @Test
  public void testPredicateNot() { dataFlow.get().doTest(); }

  @Test
  public void testEnumNullability() {
    dataFlow.get().doTest();
  }

  @Test
  public void testBoxedTypeNullability() {
    dataFlow.get().doTest();
  }

  @Test
  public void testPatternsNullability() {
    dataFlow.get().doTest();
  }

  @Test
  public void testPatterns() {
    dataFlow.get().doTest();
  }

  @Test
  public void testDeconstructionNullability() {
    dataFlow.get().doTest();
  }

  @Test
  public void testUnnamedPatterns() {
    dataFlow.get().doTest();
  }

  @Test
  public void testUnnamedPatternsJava22() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, dataFlow.get()::doTest);
  }

  @Test
  public void testPatternInStreamNotComplex() {
    dataFlow.get().doTest();
  }

  @Test
  public void testInstanceof() {
    dataFlow.get().doTest();
  }
  
  @Test
  public void testTakeWhileUpdate() { dataFlow.get().doTest(); }

  @Test
  public void testNewStringWrongEquals() { dataFlow.get().doTest(); }

  @Test
  public void testSwitchWhenReturnBoolean() { dataFlow.get().doTest(); }

  @Test
  public void testSkipSwitchExpressionWithThrow() { dataFlow.get().doTest(); }

  @Test
  public void testStringTemplates() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    setupTypeUseAnnotations("typeUse", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testChronoRange() {
    dataFlow.get().doTest();
  }

  @Test
  public void testSealedClassCast() { dataFlow.get().doTest(); }
  @Test
  public void testCastToSealedInterface() { dataFlow.get().doTest(); }

  @Test
  public void testWhenPatterns() {
    dataFlow.get().doTest();
  }
  @Test
  public void testPrecalculatedTrimValue() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchNullability() {
    dataFlow.get().doTest();
  }
  @Test
  public void testRecordPatterns() {
    dataFlow.get().doTest();
  }
  @Test
  public void testRecordPatternNested() {
    dataFlow.get().doTest();
  }
  @Test
  public void testRecordPatternAndWhen() {
    dataFlow.get().doTest();
  }
  @Test
  public void testRecordPatternWildcardComponent() {
    dataFlow.get().doTest();
  }
  @Test
  public void testNestedRecordPatterns() {
    dataFlow.get().doTest();
  }
  @Test
  public void testSuspiciousLabelElementsJava20() {
    dataFlow.get().doTest();
  }
  @Test
  public void testReadResolve() { dataFlow.get().doTest(); }
  @Test
  public void testReadResolve2() { dataFlow.get().doTest(); }
  @Test
  public void testDifferentTypesButNullable() { dataFlow.get().doTest(); }
  @Test
  public void testInstanceOfWidening() { dataFlow.get().doTest(); }
  @Test
  public void testSwitchPatternInGuard() { dataFlow.get().doTest(); }
  @Test
  public void testForEachPattern() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         public @interface Range {
                           long from();
                           long to();
                         }""");
    dataFlow.get().doTest();
  }

  @Test
  public void testArrayElementWrappedInPureMethod() { dataFlow.get().doTest(); }
  @Test
  public void testArrayAddedIntoCollection() { dataFlow.get().doTest(); }
  @Test
  public void testInstanceOfUnresolvedType() { dataFlow.get().doTest(); }

  @Test
  public void testInstanceOfPatternAffectNullity() { dataFlow.get().doTest(); }

  @Test
  public void testNullabilityInEnumSwitch() { dataFlow.get().doTest(); }
  
  @Test
  public void testSwitchBooleanWhen() { dataFlow.get().doTest(); }

  @Test
  public void testJetBrainsNotNullByDefault() {
    dataFlow.get().doTest();
  }
  
  @Test
  public void testClassFileGetter() {
    dataFlow.get().doTest();
  }
  @Test
  public void testPrivateMethodDoNotFlushFinalFields() { dataFlow.get().doTest(); }
  @Test
  public void testGetterVsDirectAccess() { dataFlow.get().doTest(); }
  @Test
  public void testGetterVsDirectAccessRecordOverriddenGetter() { dataFlow.get().doTest(); }
  @Test
  public void testGetterVsDirectAccessNonFinal() { dataFlow.get().doTest(); }
  @Test
  public void testGetterVsDirectAccessObjectEquals() { dataFlow.get().doTest(); }
  @Test
  public void testSetterAndGetter() { dataFlow.get().doTest(); }
  @Test
  public void testStaticEqualsContract() { dataFlow.get().doTest(); }
  @Test
  public void testNewExpressionAnnotations() { dataFlow.get().doTest(); }
  
  @Test
  public void testJSpecifyLocalWithGenerics() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyLocalWithGenericsWithVar() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  @TestFor(issues = "IDEA-396004")
  public void testJSpecifyVarFromGenericMethodWithDeclarationNullable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass(DataFlowInspectionTest.barNullableNick());
    myFixture.addClass("""
                         package lib;
                         public interface ResponseSpec {
                           @bar.NullableNick
                           <T> T body(Class<T> bodyType);
                         }""");
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyCallExplicitTypeParameters() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyGetOrDefault() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyReturnFromParameterized() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }
  
  @Test
  public void testJsr305NicknameAsTypeAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    dataFlow.get().doTest();
  }
  
  @Test
  public void testSwitchNoUnreachableBranchesDueToUnresolvedType() {
    dataFlow.get().doTest();
  }
  
  @Test
  public void testObjectUtilsNullMethods() {
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyReturnFromGenericFunctions() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyListOfNullable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyIntersectionTernary() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyIntersectionBound() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    myFixture.addClass("""
                         package org.jspecify.annotations;
                         import java.lang.annotation.*;

                         @Target(ElementType.TYPE_USE) public @interface NullnessUnspecified { }""");
    dataFlow.get().doTest();
  }
  
  @Test
  public void testGuavaIterablesProblems() {
    dataFlow.get().doTest();
  }
  
  @Test
  public void testGenericVarargNullability() {
    dataFlow.get().doTest();
  }

  @Test
  public void testPassthroughGenericParameter() {
    dataFlow.get().doTestWith((dfi, _) -> dfi.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }

  @Test
  public void testMutabilityJdk21() { dataFlow.get().doTest(); }
  
  @Test
  public void testJSpecifyLambdaTernary() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    dataFlow.get().doTest();
  }
  
  @Test
  public void testJSpecifyNullUnmarkedOverNullMarked() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }
  
  @Test
  public void testJSpecifySuperBound() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyArrayNullability() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testMatchExceptionNestedDeconstruction() { dataFlow.get().doTest(); }

  @Test
  public void testMatchExceptionSealedClass() { dataFlow.get().doTest(); }

  @Test
  public void testMatchExceptionSealedClassOnlyNull() {
    dataFlow.get().doTestWith((insp, _) -> insp.REPORT_UNSOUND_WARNINGS = false);
  }

  @Test
  public void testNoMatchExceptionSealedClassDataFlow() { dataFlow.get().doTest(); }

  @Test
  public void testMatchExceptionDoubleNestedDeconstruction() { dataFlow.get().doTest(); }

  @Test
  public void testNoMatchExceptionMostNestedDeconstruction() { dataFlow.get().doTest(); }

  @Test
  public void testMatchExceptionNestedSealedClass() { dataFlow.get().doTest(); }

  @Test
  public void testNoMatchExceptionNestedDeconstructionWithDefault() { dataFlow.get().doTest(); }

  @Test
  public void testNoMatchExceptionSealedClassWithNullDefault() { dataFlow.get().doTest(); }

  @Test
  public void testNoMatchExceptionDoubleNestedDeconstructionWithDominated() { dataFlow.get().doTest(); }

  @Test
  public void testNoMatchExceptionSealedClassWithDominated() { dataFlow.get().doTest(); }

  @Test
  public void testOptionalInference() {
    dataFlow.get().doTestWith((dfi, _) -> dfi.SUGGEST_NULLABLE_ANNOTATIONS = false);
  }
  
  @Test
  public void testJSpecifyNullableFieldInLambda() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest(); 
  }
  
  @Test
  public void testJSpecifyNullableTypeParameterInheritance() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyNullLiteralToTypeVariable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyNullLiteralToMethodTypeVariable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyPlainTypeVariableReturnOptionOff() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyPlainTypeVariableReturnOptionOn() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTestWith((insp, _) -> insp.REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS = true);
  }

  @Test
  public void testJSpecifyParametricNullableField() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyPlainTypeVariableFieldOptionOn() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTestWith((insp, _) -> insp.REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS = true);
  }

  @Test
  public void testJSpecifyUnboundedWildcardBoundFromUnmarkedScope() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  private void addNullnessUnspecified() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    myFixture.addClass("""
                         package org.jspecify.annotations;
                         import java.lang.annotation.*;

                         @Target(ElementType.TYPE_USE) public @interface NullnessUnspecified { }""");
  }

  @Test
  public void testFlowMethodTests() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testNullableArrayLocalVariable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }
  
  @Test
  @TestFor(issues = "IDEA-389723")
  public void testOptionalInsideLambda() {
    dataFlow.get().doTest();
  }
  
  @Test
  @TestFor(issues = "IDEA-389893")
  public void testUnboxedMethodReferenceVoidType() {
    dataFlow.get().doTest();
  }

  @Test
  public void testNullMarkedPutIfAbsent() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyNullableReturnInference() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    myFixture.addClass("""
                         package org.assertj.core.api;
                         public class AbstractAssert {}""");
    myFixture.addClass("""
                         package org.assertj.core.api;
                         public class ObjectAssert<T> extends AbstractAssert {
                           public ObjectAssert(T obj) {}
                           public ObjectAssert<T> isNull() { return this; }
                           public ObjectAssert<T> isNotNull() { return this; }
                         }""");
    myFixture.addClass("""
                         package org.assertj.core.api;
                         public class Assertions {
                           public static <T> ObjectAssert<T> assertThat(T actual) { return new ObjectAssert<>(actual); }
                         }""");
    // The stub above is source, so the suggestion would report every nullable argument of assertThat
    dataFlow.get().doTestWith((dfi, cvi) -> {
      dfi.SUGGEST_NULLABLE_ANNOTATIONS = false;
      cvi.REPORT_CONSTANT_REFERENCE_VALUES = false;
    });
  }

  @Test
  public void testJSpecifyDeconstructionTypeArgument() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    addJSpecifyNonNull(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyDiamondFunctionalArgument() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }
}

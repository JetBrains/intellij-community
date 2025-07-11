// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection21Test extends DataFlowInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testSuspiciousLabelElementsJava19() {
    doTest();
  }

  public void testParameterNullabilityFromSwitch() {
    doTest();
  }

  public void testDefaultLabelElementInSwitch() {
    doTest();
  }

  public void testSuspiciousLabelElements() {
    doTest();
  }

  public void testPredicateNot() { doTest(); }

  public void testEnumNullability() {
    doTest();
  }

  public void testBoxedTypeNullability() {
    doTest();
  }

  public void testPatternsNullability() {
    doTest();
  }

  public void testPatterns() {
    doTest();
  }

  public void testDeconstructionNullability() {
    doTest();
  }

  public void testUnnamedPatterns() {
    doTest();
  }

  public void testUnnamedPatternsJava22() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  public void testPatternInStreamNotComplex() {
    doTest();
  }

  public void testInstanceof() {
    doTest();
  }
  
  public void testTakeWhileUpdate() { doTest(); }

  public void testNewStringWrongEquals() { doTest(); }

  public void testSwitchWhenReturnBoolean() { doTest(); }

  public void testSkipSwitchExpressionWithThrow() { doTest(); }

  public void testStringTemplates() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testChronoRange() {
    doTest();
  }

  public void testSealedClassCast() { doTest(); }
  public void testCastToSealedInterface() { doTest(); }

  public void testWhenPatterns() {
    doTest();
  }
  public void testPrecalculatedTrimValue() { doTest(); }
  public void testSwitchNullability() {
    doTest();
  }
  public void testRecordPatterns() {
    doTest();
  }
  public void testRecordPatternNested() {
    doTest();
  }
  public void testRecordPatternAndWhen() {
    doTest();
  }
  public void testNestedRecordPatterns() {
    doTest();
  }
  public void testSuspiciousLabelElementsJava20() {
    doTest();
  }
  public void testReadResolve() { doTest(); }
  public void testReadResolve2() { doTest(); }
  public void testDifferentTypesButNullable() { doTest(); }
  public void testInstanceOfWidening() { doTest(); }
  public void testSwitchPatternInGuard() { doTest(); }
  public void testForEachPattern() {
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         public @interface Range {
                           long from();
                           long to();
                         }""");
    doTest();
  }

  public void testArrayElementWrappedInPureMethod() { doTest(); }
  public void testArrayAddedIntoCollection() { doTest(); }
  public void testInstanceOfUnresolvedType() { doTest(); }

  public void testInstanceOfPatternAffectNullity() { doTest(); }

  public void testNullabilityInEnumSwitch() { doTest(); }
  
  public void testSwitchBooleanWhen() { doTest(); }

  public void testJetBrainsNotNullByDefault() {
    doTest();
  }
  
  public void testClassFileGetter() {
    doTest();
  }
  public void testPrivateMethodDoNotFlushFinalFields() { doTest(); }
  public void testGetterVsDirectAccess() { doTest(); }
  public void testGetterVsDirectAccessRecordOverriddenGetter() { doTest(); }
  public void testGetterVsDirectAccessNonFinal() { doTest(); }
  public void testGetterVsDirectAccessObjectEquals() { doTest(); }
  public void testSetterAndGetter() { doTest(); }
  public void testStaticEqualsContract() { doTest(); }
  public void testNewExpressionAnnotations() { doTest(); }
  
  public void testJSpecifyLocalWithGenerics() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  public void testJSpecifyCallExplicitTypeParameters() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  public void testJSpecifyGetOrDefault() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  public void testJSpecifyReturnFromParameterized() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  public void testJsr305NicknameAsTypeAnnotation() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    doTest();
  }
  
  public void testSwitchNoUnreachableBranchesDueToUnresolvedType() {
    doTest();
  }
  
  public void testObjectUtilsNullMethods() {
    doTest();
  }

  public void testJSpecifyReturnFromGenericFunctions() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  public void testJSpecifyListOfNullable() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  public void testJSpecifyIntersectionBound() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  public void testPassthroughGenericParameter() {
    doTestWith((dfi, cvi) -> dfi.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true);
  }

  public void testMutabilityJdk21() { doTest(); }
  
  public void testJSpecifyLambdaTernary() {
    addJSpecifyNullMarked(myFixture);
    doTest();
  }
  
  public void testJSpecifyNullUnmarkedOverNullMarked() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

}
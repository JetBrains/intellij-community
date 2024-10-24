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

  public void testInstanceOfPatternAffectNullity() { doTest(); }

  public void testNullabilityInEnumSwitch() { doTest(); }

  public void testJetBrainsNotNullByDefault() {
    addJetBrainsNotNullByDefault(myFixture);
    doTest();
  }
  
  public void testClassFileGetter() {
    doTest();
  }
}
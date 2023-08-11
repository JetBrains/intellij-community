// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
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
  
  public void testUnnamedPatterns() {
    doTest();
  }
  
  public void testPatternInStreamNotComplex() {
    doTest();
  }

  public void testInstanceof() {
    doTest();
  }

  public void testNewStringWrongEquals() { doTest(); }

  public void testSkipSwitchExpressionWithThrow() { doTest(); }

  public void testStringTemplates() {
    myFixture.addClass("""
                         package java.lang;
                         import java.util.*;
                         public interface StringTemplate {
                           List<String> fragments();
                           List<Object> values();
                           native static StringTemplate of(String string);
                           Processor<String, RuntimeException> STR;
                           Processor<StringTemplate, RuntimeException> RAW;
                           interface Processor<R, E extends Throwable> {
                             R process(StringTemplate stringTemplate) throws E;
                           }
                         }""");
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
}
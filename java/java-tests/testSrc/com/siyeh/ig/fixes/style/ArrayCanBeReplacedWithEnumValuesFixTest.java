// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.ArrayCanBeReplacedWithEnumValuesInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;


@TestApplication
public class ArrayCanBeReplacedWithEnumValuesFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ArrayCanBeReplacedWithEnumValuesInspection()), "style/array_replaced_enum_values", null, null,
                        getEnvironmentClasses());

  @Test
  public void testClassWithEnum() { doTest("TestEnum"); }

  @Test
  public void testEnumWithField() {doTest("TestEnum");}

  @Test
  public void testNotEnumInit() { assertQuickfixNotAvailable(); }

  @Test
  public void testNotEnumMulti() { assertQuickfixNotAvailable(); }

  @Test
  public void testEnumRevOrder() { assertQuickfixNotAvailable();}

  @Test
  public void testErrorInMultiDArray() { assertQuickfixNotAvailable();}

  @Test
  public void testMultiDArrayNoError() {assertQuickfixNotAvailable();}

  @Test
  public void testInnerEnum() {doTest("Inner");}

  @Test
  public void testFooInit() {assertQuickfixNotAvailable();}

  @Test
  public void testOuterEnumUse() {doTest("TestEnum");}
  
  @Test
  public void testParentheses() {doTest("X");}



  private void doTest(String hint) {
    ig.get().doTest(InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.quickfix", hint));
  }

  private void assertQuickfixNotAvailable() {
    String message = InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.quickfix", "@");
    ig.get().assertQuickfixNotAvailable(message.substring(0, message.indexOf('@')));
  }


  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
public class OuterEnum {
    public enum TestEnum {
        A, B, C
    }
}"""
    };
  }
}


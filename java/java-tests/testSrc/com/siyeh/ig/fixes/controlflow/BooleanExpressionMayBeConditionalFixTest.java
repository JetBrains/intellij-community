/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.BooleanExpressionMayBeConditionalInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class BooleanExpressionMayBeConditionalFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new BooleanExpressionMayBeConditionalInspection()));

  @Test
  public void testSimple() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
                 """
                   void test(boolean foo, boolean bar) {
                       boolean c = false;
                       boolean b = (!foo &&/**/ (c = bar)) || (foo && true);}""",
                 """
                   void test(boolean foo, boolean bar) {
                       boolean c = false;
                       boolean b = foo ? true : (c = bar);}"""
                 );
  }

  @Test
  public void testSideEffects1() {
    ig.get().doTest(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = b && foo ||/**/ !b && complex();" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}",
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = b ? foo : complex();" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}");
  }

  @Test
  public void testSideEffects2() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
           "class X {" +
           "  boolean b = true;" +
           "  void test(boolean foo) {" +
           "    boolean c = complex() && foo ||/**/ !complex() && b;" +
           "  }" +
           "  boolean complex() {" +
           "    b = !b;" +
           "    return true;" +
           "  }" +
           "}");
  }

  @Test
  public void testComparison() {
    ig.get().doTest(InspectionGadgetsBundle.message("if.may.be.conditional.quickfix"),
           """
             class X {
               boolean test(int x, int y, int z) {
                 return (x > y && z == 1) || /**/(x <= y && z == 2);
               }
             }""",
           """
             class X {
               boolean test(int x, int y, int z) {
                 return x > y ? z == 1 : z == 2;
               }
             }""");
  }

}

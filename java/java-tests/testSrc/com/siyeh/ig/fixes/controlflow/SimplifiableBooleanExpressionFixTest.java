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
import com.siyeh.ig.controlflow.SimplifiableBooleanExpressionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class SimplifiableBooleanExpressionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new SimplifiableBooleanExpressionInspection()));

  @Test
  public void testSimple() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "void test(boolean foo, boolean bar) {" +
                 "    boolean d = !(foo ^ /**/(bar != true));" +
                 "}",
                 "void test(boolean foo, boolean bar) {" +
                 "    boolean d = foo == (bar != true);" +
                 "}");
  }

  @Test
  public void testAndOrExpression() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(boolean a, boolean b) {" +
                 "    return a && b /**/|| !a;" +
                 "}",
                 "boolean fff(boolean a, boolean b) {" +
                 "    return !a || b;" +
                 "}");
  }

  @Test
  public void testAndOrExpression3() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return a && b && c/**/|| !a;" +
                 "}",
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return !a || b && c;" +
                 "}");
  }

  @Test
  public void testAndOrExpression3Middle() {
    // While this particular case could be safely transformed to "a && c || !b", the order of execution is changed which may
    // affect dereferencing (e.g. "a != null && b != null && a.foo(b.bar()) || b == null") is safe, but replacement is not.
    // Proper replacement would be "(a || !b) && (!b || c)", but it's not shorter than the original code
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                               """
                                 class X {
                                   boolean fff(boolean a, boolean b, boolean c) {\s
                                     return a && b && c/**/ || !b;
                                   }
                                 }""");
  }

  @Test
  public void testIncomplete() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                               """
                                 class X {
                                   boolean fff(boolean a) {\s
                                     return a/**/ && || !a;

                                   }
                                 }""");
  }

  @Test
  public void testAndOrExpression3Parentheses() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return (a && b && !c)/**/|| c;" +
                 "}",
                 "boolean fff(boolean a, boolean b, boolean c) {" +
                 "    return (a && b) || c;" +
                 "}");
  }

  @Test
  public void testAndOrExpressionComparisons() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(int a, int b, int c) {" +
                 "    return a > b && b > c /**/|| a <= b;" +
                 "}",
                 "boolean fff(int a, int b, int c) {" +
                 "    return a <= b || b > c;" +
                 "}");
  }

  @Test
  public void testAndOrNonNegated() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix"),
                 "boolean fff(int a, int b, int c) {" +
                 "    return a > b && b > c && b > 0 /**/|| (b > c);" +
                 "}",
                 "boolean fff(int a, int b, int c) {" +
                 "    return b > c;" +
                 "}");
  }

}

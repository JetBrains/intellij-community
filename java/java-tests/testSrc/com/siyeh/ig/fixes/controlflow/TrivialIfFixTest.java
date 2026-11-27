/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.TrivialIfInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class TrivialIfFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new TrivialIfInspection()), "controlflow/trivialIf", "Simplify 'if else'",
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_14));

  @Test
  public void testComments() { ig.get().doTest(); }
  @Test
  public void testCommentsInAssignment() { ig.get().doTest(); }
  @Test
  public void testAssignmentNegated() { ig.get().doTest(); }
  @Test
  public void testImplicitAssignment() { ig.get().doTest(); }
  @Test
  public void testImplicitAssignmentAnd() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testNegatedConditional() { ig.get().doTest(); }
  @Test
  public void testNegatedConditional1() { ig.get().doTest(); }
  @Test
  public void testAssert1() { ig.get().doTest(); }
  @Test
  public void testAssert2() { ig.get().doTest(); }
  @Test
  public void testParentheses() { ig.get().doTest(); }
  @Test
  public void testNested() { ig.get().doTest(); }
  @Test
  public void testInCodeBlock() { ig.get().doTest(); }
  @Test
  public void testInSwitch() { ig.get().doTest(); }
  @Test
  public void testRedundantComparison1() { ig.get().doTest(); }
  @Test
  public void testRedundantComparison2() { ig.get().doTest(); }
  @Test
  public void testRedundantComparisonNested() { ig.get().doTest(); }
  @Test
  public void testRedundantComparisonNested2() { ig.get().doTest(); }
  @Test
  public void testRedundantComparisonNested3() { ig.get().doTest(); }
  @Test
  public void testRedundantComparisonDouble() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testMethodCall() { ig.get().doTest(); }
  @Test
  public void testMethodCall2() { ig.get().doTest(); }
  @Test
  public void testYield() { ig.get().doTest(); }
  @Test
  public void testOverwrittenDeclaration() { ig.get().doTest(); }
}
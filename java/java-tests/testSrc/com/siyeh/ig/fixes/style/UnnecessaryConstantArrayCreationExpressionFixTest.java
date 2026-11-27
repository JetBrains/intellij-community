// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.UnnecessaryConstantArrayCreationExpressionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UnnecessaryConstantArrayCreationExpressionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryConstantArrayCreationExpressionInspection()),
                        "style/unnecessary_constant_array_creation_expression");

  @Test
  public void testPrimitive() { doTest("int[]"); }
  @Test
  public void testParenthesized() { doTest("int[]"); }
  @Test
  public void testTwoDimension() { doTest("Map[][]"); }
  @Test
  public void testInitializerWithoutNew() { assertQuickfixNotAvailable(); }

  private void doTest(String hint) {
    ig.get().doTest(CommonQuickFixBundle.message("fix.remove", "new " + hint));
  }

  private void assertQuickfixNotAvailable() {
    ig.get().assertQuickfixNotAvailable(CommonQuickFixBundle.message("fix.remove", ""));
  }

}

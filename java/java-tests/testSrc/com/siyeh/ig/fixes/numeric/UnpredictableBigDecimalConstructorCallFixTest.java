// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.numeric.UnpredictableBigDecimalConstructorCallInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnpredictableBigDecimalConstructorCallFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    UnpredictableBigDecimalConstructorCallInspection inspection = new UnpredictableBigDecimalConstructorCallInspection();
    inspection.ignoreReferences = false;
    return List.of(inspection);
  }, "numeric/unpredictable_big_decimal");

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.addClass(
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal(double d) {}" +
      "}"
    );
  }

  @Test
  public void testFactory() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "BigDecimal.valueOf(val)"));
  }

  @Test
  public void testConstructor() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "new BigDecimal(\"0.1\")"));
  }

  @Test
  public void testLiteral() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "new BigDecimal(\"2\")"));
  }

  @Test
  public void testUnderscores() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "new BigDecimal(\"1000.1\")"));
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.StringEqualsEmptyStringInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class StringEqualsEmptyStringFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    StringEqualsEmptyStringInspection inspection = new StringEqualsEmptyStringInspection();
    inspection.SUPPRESS_FOR_VALUES_WHICH_COULD_BE_NULL = false;
    return List.of(inspection);
  }, "performance/replace_with_isempty", CommonQuickFixBundle.message("fix.replace.with.x", "isEmpty()"));

  @Test
  public void testSimple() { ig.get().doTest(); }
  @Test
  public void testNullCheck() { ig.get().doTest(); }
  @Test
  public void testNullCheckSuppress() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.enableInspections(new StringEqualsEmptyStringInspection());
    ig.get().assertQuickfixNotAvailable();
  }
  @Test
  public void testNullCheckAlreadyPresent() { ig.get().doTest(); }
  @Test
  public void testTernary() { ig.get().doTest(); }
  @Test
  public void testNullCheckNegation() { ig.get().doTest(); }
  @Test
  public void testTernaryNegation() { ig.get().doTest(); }
}

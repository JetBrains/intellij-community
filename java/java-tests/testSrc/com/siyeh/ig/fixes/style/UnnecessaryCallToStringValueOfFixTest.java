// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.UnnecessaryCallToStringValueOfInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UnnecessaryCallToStringValueOfFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(), "style/unnecessary_valueof", "Fix all 'Unnecessary conversion to 'String'' problems in file");

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    UnnecessaryCallToStringValueOfInspection inspection = new UnnecessaryCallToStringValueOfInspection();
    if (ig.get().getTestName(true).contains("_all")) {
      inspection.reportWithEmptyString = true;
    }
    myFixture.enableInspections(inspection);
  }

  @Test
  public void testUnnecessaryCall() { ig.get().doTest(); }
  @Test
  public void testUnnecessaryCall_all() { ig.get().doTest(); }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.UnnecessaryTemporaryOnConversionFromStringInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UnnecessaryTemporaryOnConversionFromStringFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryTemporaryOnConversionFromStringInspection()),
                        "performance/unnecessary_temporary_from_string");

  @Test
  public void testIntegerIntValue() { ig.get().doTest("Replace with 'Integer.parseInt(s+/*double-s*/s)'"); }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.ManualArrayCopyInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ManualArrayCopyFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ManualArrayCopyInspection()), "performance/replace_with_system_arraycopy",
                        CommonQuickFixBundle.message("fix.replace.with.x", "System.arraycopy()"));

  @Test
  public void testSimple() { ig.get().doTest(); }
  @Test
  public void testDecrement() { ig.get().doTest(); }
  @Test
  public void testLengthSmallerThanOffset() { ig.get().doTest(); }
  @Test
  public void testInitializerContainsSubtraction() { ig.get().doTest(); }
  @Test
  public void testNotEqual() { ig.get().doTest(); }
}
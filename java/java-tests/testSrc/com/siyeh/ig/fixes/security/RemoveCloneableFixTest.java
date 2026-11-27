// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.security;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.security.CloneableClassInSecureContextInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class RemoveCloneableFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new CloneableClassInSecureContextInspection()), "security/remove_cloneable",
                        InspectionGadgetsBundle.message("remove.cloneable.quickfix"));

  @Test
  public void testCloneUsed() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("remove.cloneable.quickfix"));
  }

  @Test
  public void testSimple() { ig.get().doTest(); }
  @Test
  public void testSafeCloneCall() { ig.get().doTest(); }
}

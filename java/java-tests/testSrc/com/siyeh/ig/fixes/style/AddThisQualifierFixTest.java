// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class AddThisQualifierFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnqualifiedFieldAccessInspection()), "style/add_this_qualifier",
                        InspectionGadgetsBundle.message("add.this.qualifier.quickfix"));

  @Test
  public void testSimple() {
    ig.get().doTest();
  }

  @Test
  public void testStaticClass() {
    ig.get().doTest();
  }
}

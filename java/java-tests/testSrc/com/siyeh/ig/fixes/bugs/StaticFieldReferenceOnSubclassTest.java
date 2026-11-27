// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.bugs.StaticFieldReferenceOnSubclassInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class StaticFieldReferenceOnSubclassTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new StaticFieldReferenceOnSubclassInspection()), "bugs/staticFieldRefOnSubclass");

  @Test
  public void testPreserveComments() {
    ig.get().doTest(ig.get().getTestName(true), InspectionGadgetsBundle.message("static.field.via.subclass.rationalize.quickfix"));
  }

}

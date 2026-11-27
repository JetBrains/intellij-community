// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.classlayout;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.classlayout.UtilityClassWithPublicConstructorInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UtilityClassWithPublicConstructorFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UtilityClassWithPublicConstructorInspection()),
                        "classlayout/utility_class_with_public_constructor",
                        InspectionsBundle.message("fix.all.inspection.problems.in.file", InspectionGadgetsBundle.message("utility.class.with.public.constructor.display.name")));

  @Test
  public void testClassWithInheritor() {
    ig.get().doTest();
  }
  @Test
  public void testPublicClass() {
    ig.get().doTest();
  }
}

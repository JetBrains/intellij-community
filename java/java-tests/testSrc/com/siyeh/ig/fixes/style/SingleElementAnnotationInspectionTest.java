// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.annotation.SingleElementAnnotationInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class SingleElementAnnotationInspectionTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new SingleElementAnnotationInspection()), "style/expand_annotation",
                        InspectionGadgetsBundle.message("single.element.annotation.quickfix"));

  @Test
  public void testOneAttr() {
    ig.get().doTest();
  }

  @Test
  public void testMultiAttr() {
    ig.get().doTest();
  }

  @Test
  public void testAlreadyHasName() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testAnnotationAttr() {
    ig.get().doTest();
  }

  @Test
  public void testArrayAttr() {
    ig.get().doTest();
  }

  @Test
  public void testArrayItemAttr() {
    ig.get().doTest();
  }

  @Test
  public void testIncompatibleType() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testIncompatibleArrayItemType() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testNoValueAttr() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testNameAlreadyUsed() {
    ig.get().assertQuickfixNotAvailable();
  }

}

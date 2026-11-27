// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.abstraction;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.abstraction.TypeMayBeWeakenedInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class TypeMayBeWeakenedFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(), "abstraction/type_may_be_weakened", null,
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_10));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    final TypeMayBeWeakenedInspection inspection = new TypeMayBeWeakenedInspection();
    inspection.onlyWeakentoInterface = false;
    inspection.doNotWeakenReturnType = false;
    inspection.doNotWeakenInferredVariableType = false;
    inspection.myStopClassSet = new OrderedSet<>(Collections.singletonList("com.siyeh.igfixes.abstraction.type_may_be_weakened.Stop"));
    myFixture.enableInspections(inspection);
    ImplicitUsageProvider.EP_NAME.getExtensionList();
    EntryPointsManagerBase.DEAD_CODE_EP_NAME.getExtensionList();
  }

  @Test
  public void testShorten() { ig.get().doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "java.util.Collection")); }
  @Test
  public void testLocalClass() { ig.get().doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "A")); }

  @Test
  public void testGeneric() {
    ig.get().doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.C"));
  }

  @Test
  public void testStopClass() {
    ig.get().doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.Stop"));
  }

  @Test
  public void testJava10() {
    ig.get().doTest(InspectionGadgetsBundle.message("inspection.type.may.be.weakened.quickfix", "com.siyeh.igfixes.abstraction.type_may_be_weakened.B"));
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.dataflow;

import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class CreateNullBranchFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new DataFlowInspection()), "dataflow/create_null_branch", null,
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_21));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Test
  public void testNoDefault() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Test
  public void testDefaultExists() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Test
  public void testPrevStatementCompletesNormally() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Test
  public void testRuleWithNoDefault() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Test
  public void testRuleWithDefaultExists() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Test
  public void testNullAlreadyExists() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Test
  public void testUnconditionalPatternExists() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  @Test
  public void testUnconditionalPatternNotExist() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }
  
  @Test
  public void testEmptyBody() {
    ig.get().doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.dataflow;

import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;

public class CreateNullBranchFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DataFlowInspection());
    myRelativePath = "dataflow/create_null_branch";
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_21);
  }

  public void testNoDefault() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  public void testDefaultExists() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  public void testPrevStatementCompletesNormally() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  public void testRuleWithNoDefault() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  public void testRuleWithDefaultExists() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  public void testNullAlreadyExists() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  public void testUnconditionalPatternExists() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }

  public void testUnconditionalPatternNotExist() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }
  
  public void testEmptyBody() {
    doTest(InspectionGadgetsBundle.message("create.null.branch.fix.family.name"));
  }
}

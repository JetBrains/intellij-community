// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.ForEachWithRecordPatternCanBeUsedInspection;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ForEachWithRecordPatternCanBeUsedInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testSimpleWarning() { checkHighlight(); }

  public void testPreview(){
    checkAction(true, false, false);
  }

  public void testSimple(){
    checkAction(false, false, true);
  }
  public void testUnusedCounts(){
    checkAction(false, true, true);
  }
  public void testLevel(){
    checkAction(false, true, true);
  }
  public void testComponentCounts(){
    checkAction(false, true, true);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_20;
  }


  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/forEachWithRecordPatternCanBeUsed";
  }

  private void checkHighlight() {
    myFixture.enableInspections(new ForEachWithRecordPatternCanBeUsedInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  private void checkAction(boolean checkPreview, boolean useCustomProperties, boolean checkAll) {
    ForEachWithRecordPatternCanBeUsedInspection inspection = new ForEachWithRecordPatternCanBeUsedInspection();
    if (useCustomProperties) {
      inspection.forceUseVar = true;
      inspection.maxLevel = 1;
      inspection.maxComponentCounts = 3;
      inspection.maxNotUsedComponentCounts = 1;
    }
    myFixture.enableInspections(inspection);
    myFixture.configureByFiles("before" + getTestName(false) + ".java");

    IntentionAction action = myFixture.findSingleIntention(InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.fix.family.name"));
    assertNotNull(action);
    if (checkAll) {
      action = IntentionManager.getInstance().createFixAllIntention(new LocalInspectionToolWrapper(inspection), action);
    }

    if (checkPreview) {
      myFixture.checkPreviewAndLaunchAction(action);
    }
    else {
      myFixture.launchAction(action);
    }

    myFixture.checkResultByFile("before" + getTestName(false) + ".java", "after" + getTestName(false) + ".java", true);
  }
}

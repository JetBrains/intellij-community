// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.ForEachWithRecordPatternCanBeUsedInspection;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ForEachWithRecordPatternCanBeUsedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_X);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/forEachWithRecordPatternCanBeUsed");
  }

  @Test
  public void testSimpleWarning() { checkHighlight(); }

  @Test
  public void testPreview(){
    checkAction(true, false, false);
  }

  @Test
  public void testSimple(){
    checkAction(false, false, true);
  }
  @Test
  public void testUnusedCounts(){
    checkAction(false, true, true);
  }
  @Test
  public void testLevel(){
    checkAction(false, true, true);
  }
  @Test
  public void testComponentCounts(){
    checkAction(false, true, true);
  }

  private void checkHighlight() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new ForEachWithRecordPatternCanBeUsedInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  private void checkAction(boolean checkPreview, boolean useCustomProperties, boolean checkAll) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ForEachWithRecordPatternCanBeUsedInspection inspection = new ForEachWithRecordPatternCanBeUsedInspection();
    if (useCustomProperties) {
      inspection.forceUseVar = true;
      inspection.maxLevel = 1;
      inspection.maxComponentCounts = 3;
      inspection.maxNotUsedComponentCounts = 1;
    }
    myFixture.enableInspections(inspection);
    myFixture.configureByFiles("before" + myFixture.getTestName(false) + ".java");

    IntentionAction action = myFixture.findSingleIntention(InspectionGadgetsBundle.message("inspection.enhanced.for.with.record.pattern.can.be.used.fix.family.name"));
    assertNotNull(action);
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction fix = action;
      if (checkAll) {
        fix = IntentionManager.getInstance().createFixAllIntention(new LocalInspectionToolWrapper(inspection), action);
      }

      if (checkPreview) {
        myFixture.checkPreviewAndLaunchAction(fix);
      }
      else {
        myFixture.launchAction(fix);
      }

      myFixture.checkResultByFile("before" + myFixture.getTestName(false) + ".java", "after" + myFixture.getTestName(false) + ".java", true);
    });
  }
}

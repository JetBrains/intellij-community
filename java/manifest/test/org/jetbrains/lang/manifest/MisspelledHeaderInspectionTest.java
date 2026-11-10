// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.lang.manifest;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.lang.manifest.highlighting.MisspelledHeaderInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class MisspelledHeaderInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNoProblem() {
    doTest("Manifest-Version: 1.0", 0);
  }

  @Test
  public void testMixedCase() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">manifest-version</weak_warning>: 1.0", 1);
  }

  @Test
  public void testMissedDash() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">ManifestVersion</weak_warning>: 1.0", 1);
  }

  @Test
  public void testMisspelled() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">MainFestVersion</weak_warning>: 1.0", 1);
  }

  @Test
  public void testTotallyIncorrect() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">some_totally_impossible_header</weak_warning>: -", 0);
  }

  @Test
  public void testFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MisspelledHeaderInspection());
    myFixture.configureByText(ManifestFileType.INSTANCE, "ManifestVersion: 1.0\n");
    List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Change to");
    assertEquals(1, intentions.size());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(intentions.get(0));
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResult("Manifest-Version: 1.0\n");
    });
  }

  @Test
  public void testCustomHeader() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    MisspelledHeaderInspection inspection = new MisspelledHeaderInspection();
    inspection.CUSTOM_HEADERS.add("Custom-Header");
    myFixture.enableInspections(inspection);
    myFixture.configureByText(ManifestFileType.INSTANCE, "Custom-Header: -\n");
    myFixture.checkHighlighting();
  }

  @Test
  public void testCustomHeaderFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    try {
      InspectionProfileImpl.INIT_INSPECTIONS = true;
      myFixture.enableInspections(MisspelledHeaderInspection.class);
      myFixture.configureByText(ManifestFileType.INSTANCE, "Custom-Header: -\n");
      IntentionAction intention = myFixture.findSingleIntention(ManifestBundle.message("inspection.header.remember.fix", "Custom-Header"));
      InspectionProfileImpl profile = InspectionProfileManager.getInstance(myFixture.getProject()).getCurrentProfile();
      assertEquals(List.of(), ((MisspelledHeaderInspection)profile.getToolById("MisspelledHeader", myFixture.getFile()).getTool()).CUSTOM_HEADERS);
      myFixture.launchAction(intention);
      assertEquals(List.of("Custom-Header"), ((MisspelledHeaderInspection)profile.getToolById("MisspelledHeader", myFixture.getFile()).getTool()).CUSTOM_HEADERS);
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
  }

  private void doTest(String text, int expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MisspelledHeaderInspection());
    myFixture.configureByText(ManifestFileType.INSTANCE, text + "\n");
    myFixture.checkHighlighting();
    assertEquals(expected, myFixture.filterAvailableIntentions("Change to").size());
  }
}

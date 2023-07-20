// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.lang.manifest;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.lang.manifest.highlighting.MisspelledHeaderInspection;

import java.util.Collections;
import java.util.List;

public class MisspelledHeaderInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNoProblem() {
    doTest("Manifest-Version: 1.0", 0);
  }

  public void testMixedCase() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">manifest-version</weak_warning>: 1.0", 1);
  }

  public void testMissedDash() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">ManifestVersion</weak_warning>: 1.0", 1);
  }

  public void testMisspelled() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">MainFestVersion</weak_warning>: 1.0", 1);
  }

  public void testTotallyIncorrect() {
    doTest("<weak_warning descr=\"Header name is unknown or spelled incorrectly\">some_totally_impossible_header</weak_warning>: -", 0);
  }

  public void testFix() {
    myFixture.enableInspections(new MisspelledHeaderInspection());
    myFixture.configureByText(ManifestFileType.INSTANCE, "ManifestVersion: 1.0\n");
    List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Change to");
    assertEquals(1, intentions.size());
    myFixture.checkPreviewAndLaunchAction(intentions.get(0));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResult("Manifest-Version: 1.0\n");
  }

  public void testCustomHeader() {
    MisspelledHeaderInspection inspection = new MisspelledHeaderInspection();
    inspection.CUSTOM_HEADERS.add("Custom-Header");
    myFixture.enableInspections(inspection);
    myFixture.configureByText(ManifestFileType.INSTANCE, "Custom-Header: -\n");
    myFixture.checkHighlighting();
  }

  public void testCustomHeaderFix() {
    MisspelledHeaderInspection inspection = new MisspelledHeaderInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByText(ManifestFileType.INSTANCE, "Custom-Header: -\n");
    List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Add ");
    assertEquals(1, intentions.size());
    myFixture.launchAction(intentions.get(0));
    assertEquals(Collections.singletonList("Custom-Header"), inspection.CUSTOM_HEADERS);
  }

  private void doTest(String text, int expected) {
    myFixture.enableInspections(new MisspelledHeaderInspection());
    myFixture.configureByText(ManifestFileType.INSTANCE, text + "\n");
    myFixture.checkHighlighting();
    assertEquals(expected, myFixture.filterAvailableIntentions("Change to").size());
  }
}

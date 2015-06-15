/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.lang.manifest;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.lang.manifest.highlighting.MisspelledHeaderInspection;

import java.util.Collections;
import java.util.List;

public class MisspelledHeaderInspectionTest extends LightCodeInsightFixtureTestCase {
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
    myFixture.configureByText(ManifestFileTypeFactory.MANIFEST, "ManifestVersion: 1.0\n");
    List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Change to");
    assertEquals(1, intentions.size());
    myFixture.launchAction(intentions.get(0));
    myFixture.checkResult("Manifest-Version: 1.0\n");
  }

  public void testCustomHeader() {
    MisspelledHeaderInspection inspection = new MisspelledHeaderInspection();
    inspection.CUSTOM_HEADERS.add("Custom-Header");
    myFixture.enableInspections(inspection);
    myFixture.configureByText(ManifestFileTypeFactory.MANIFEST, "Custom-Header: -\n");
    myFixture.checkHighlighting();
  }

  public void testCustomHeaderFix() {
    MisspelledHeaderInspection inspection = new MisspelledHeaderInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByText(ManifestFileTypeFactory.MANIFEST, "Custom-Header: -\n");
    List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Add ");
    assertEquals(1, intentions.size());
    myFixture.launchAction(intentions.get(0));
    assertEquals(Collections.singleton("Custom-Header"), inspection.CUSTOM_HEADERS);
  }

  private void doTest(String text, int expected) {
    myFixture.enableInspections(new MisspelledHeaderInspection());
    myFixture.configureByText(ManifestFileTypeFactory.MANIFEST, text + "\n");
    myFixture.checkHighlighting();
    assertEquals(expected, myFixture.filterAvailableIntentions("Change to").size());
  }
}

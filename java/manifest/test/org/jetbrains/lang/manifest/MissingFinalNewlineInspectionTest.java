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
import org.jetbrains.lang.manifest.highlighting.MissingFinalNewlineInspection;

public class MissingFinalNewlineInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new MissingFinalNewlineInspection());
  }

  public void testEmptyFile() {
    myFixture.configureByText(ManifestFileTypeFactory.MANIFEST, "");
    assertEquals(0, myFixture.getAvailableIntentions().size());
  }

  public void testNoProblem() {
    myFixture.configureByText(ManifestFileTypeFactory.MANIFEST, "Manifest-Version: 1.0\n");
    assertEquals(0, myFixture.getAvailableIntentions().size());
  }

  public void testFix() {
    myFixture.configureByText(ManifestFileTypeFactory.MANIFEST, "Manifest-Version: 1.0");
    IntentionAction intention = myFixture.findSingleIntention(ManifestBundle.message("inspection.newline.fix"));
    myFixture.launchAction(intention);
    myFixture.checkResult("Manifest-Version: 1.0\n");
  }
}

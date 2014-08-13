/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.LightCompletionTestCase;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class ManifestCompletionTest extends LightCompletionTestCase {
  public void testHeaderNameCompletionVariants() {
    LightPlatformCodeInsightTestCase.configureFromFileText("MANIFEST.MF", "Specification-V<caret>\n");
    complete();
    assertContainsItems("Specification-Vendor", "Specification-Version");
    assertNotContainItems("Specification-Title");
  }

  public void testHeaderNameEnterCompletion() {
    LightPlatformCodeInsightTestCase.configureFromFileText("MANIFEST.MF", "Specification-V<caret>\n");
    complete();
    assertContainsItems("Specification-Vendor");
    selectItem(myItems[0], '\n');
    checkResultByText("Specification-Vendor: <caret>\n");
  }

  public void testHeaderNameColonCompletion() {
    LightPlatformCodeInsightTestCase.configureFromFileText("MANIFEST.MF", "Specification-V<caret>\n");
    complete();
    assertContainsItems("Specification-Vendor");
    selectItem(myItems[0], ':');
    checkResultByText("Specification-Vendor: <caret>\n");
  }

  public void testHeaderNameSpaceCompletion() {
    LightPlatformCodeInsightTestCase.configureFromFileText("MANIFEST.MF", "Specification-V<caret>\n");
    complete();
    assertContainsItems("Specification-Vendor");
    selectItem(myItems[0], ' ');
    checkResultByText("Specification-Vendor: <caret>\n");
  }
}

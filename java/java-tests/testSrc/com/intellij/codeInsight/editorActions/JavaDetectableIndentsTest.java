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
package com.intellij.codeInsight.editorActions;

import com.intellij.psi.codeStyle.autodetect.DetectableIndentOptionsProvider;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author Rustam Vishnyakov
 */
public class JavaDetectableIndentsTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/editorActions/detectableIndents/";

  public void testSpaceIndent() {
    doTest();
  }

  public void testTabIndent() {
    doTest();
  }

  private void doTest() {
    DetectableIndentOptionsProvider provider = DetectableIndentOptionsProvider.getInstance();
    assertNotNull("DetectableIndentOptionsProvider not found", provider);
    String testName = getTestName(true);
    provider.setEnabledInTest(true);
    try {
      configureByFile(BASE_PATH + testName + ".java");
      EditorTestUtil.performTypingAction(getEditor(), '\n');
      checkResultByFile(BASE_PATH + testName + "_after.java");
    }
    finally {
      provider.setEnabledInTest(false);
    }
  }
}

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
package com.intellij.codeInsight.editorActions;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class JavadocTypedHandlerFunctionalTest extends LightPlatformCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/editorActions/javadocTypedHandler/";

  public void testEmptyTag() {
    doTest();
  }

  public void testComment() {
    doTest();
  }

  public void testCodeTag() {
    doTest();
  }
  
  public void testTypeParam() {
    doTest();
  }

  private void doTest() {
    String testName = getTestName(true);
    configureByFile(BASE_PATH + testName + ".java");
    type('>');
    checkResultByFile(BASE_PATH + testName + "_after.java");
  }

}

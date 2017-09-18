/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvLVTIHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advLVTI";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_X);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_9, getModule(), getTestRootDisposable());
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }

  public void testSimpleAvailability() {
    doTest();
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }
}

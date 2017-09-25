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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

public class Java8RegressionTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/regression";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  public void testIDEA136856() {
    doTest();
  }

  public void testIDEA136887() {
    doTest();
  }

  public void testIDEA136840() {
    doTest();
  }

  public void testIDEA137277() {
    doTest();
  }

  public void testIDEA137694() {
    doTest();
  }

  public void testIDEA137668() {
    doTest();
  }

  public void testIDEA137795() {
    doTest();
  }

  public void testIDEA137893() {
    doTest();
  }

  public void testIDEA138696() {
    doTest();
  }

  public void testIDEA131282() {
    doTest(false);
  }

  public void testIDEA134945() {
    doTest(false);
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }
}

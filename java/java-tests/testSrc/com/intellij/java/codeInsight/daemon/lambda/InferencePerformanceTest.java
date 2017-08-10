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
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;

public class InferencePerformanceTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/performance";

  public void testPolyMethodCallArgumentPassedToVarargs() {
    PlatformTestUtil.startPerformanceTest("50 poly method calls passed to Arrays.asList", 4000, this::doTest).usesAllCPUCores().assertTiming();
  }

  public void testDiamondConstructorCallPassedToVarargs() {
    PlatformTestUtil.startPerformanceTest("50 diamond constructor calls passed to Arrays.asList", 12000, this::doTest).usesAllCPUCores().assertTiming();
  }

  private void doTest() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false);
  }
}

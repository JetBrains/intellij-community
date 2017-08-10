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

public class TypeMatchingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typeMatching";

  public void testIf() {
    doTest("If.java");
  }

  public void testWhile() {
    doTest("While.java");
  }

  public void testFor() {
    doTest("For1.java");
    doTest("For2.java");
  }

  public void testShortConstWithCast() {
    doTest("ShortConstWithCast.java");
  }

  protected void doTest(String filePath) {
    super.doTest(BASE_PATH + "/" + filePath, false, false);
  }
}
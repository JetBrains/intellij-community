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

public class UncompleteConstructsTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/uncompleteConstructs";

  public void testIf1() { doTest(); }
  public void testIf2() { doTest(); }
  public void testIf3() { doTest(); }
  public void testIf4() { doTest(); }
  public void testIf5() { doTest(); }
  public void testIf6() { doTest(); }

  public void testWhile1() { doTest();}
  public void testWhile2() { doTest();}
  public void testWhile3() { doTest();}
  public void testWhile4() { doTest();}
  public void testWhile5() { doTest();}

  public void testFor1() { doTest(); }
  public void testFor2() { doTest(); }
  public void testFor3() { doTest(); }
  public void testFor4() { doTest(); }
  public void testFor5() { doTest(); }
  public void testFor6() { doTest(); }
  public void testFor7() { doTest(); }
  public void testFor8() { doTest(); }
  public void testFor9() { doTest(); }
  public void testFor10() { doTest(); }

  public void testReturn1() { doTest(); }
  public void testReturn2() { doTest(); }

  private void doTest() {
    doTest(getTestName(false)+".java");
  }
  protected void doTest(String filePath) {
    super.doTest(BASE_PATH + "/" + filePath, false, false);
  }
}
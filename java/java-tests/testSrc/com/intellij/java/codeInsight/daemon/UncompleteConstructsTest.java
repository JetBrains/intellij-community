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
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;


@TestApplication
public class UncompleteConstructsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/uncompleteConstructs";

  @Test public void testIf1() { doTest(); }
  @Test public void testIf2() { doTest(); }
  @Test public void testIf3() { doTest(); }
  @Test public void testIf4() { doTest(); }
  @Test public void testIf5() { doTest(); }
  @Test public void testIf6() { doTest(); }

  @Test public void testWhile1() { doTest();}
  @Test public void testWhile2() { doTest();}
  @Test public void testWhile3() { doTest();}
  @Test public void testWhile4() { doTest();}
  @Test public void testWhile5() { doTest();}

  @Test public void testFor1() { doTest(); }
  @Test public void testFor2() { doTest(); }
  @Test public void testFor3() { doTest(); }
  @Test public void testFor4() { doTest(); }
  @Test public void testFor5() { doTest(); }
  @Test public void testFor6() { doTest(); }
  @Test public void testFor7() { doTest(); }
  @Test public void testFor8() { doTest(); }
  @Test public void testFor9() { doTest(); }
  @Test public void testFor10() { doTest(); }

  @Test public void testReturn1() { doTest(); }
  @Test public void testReturn2() { doTest(); }

  private void doTest() {
    doTest(daemonFixture.get().getFixture().getTestName(false)+".java");
  }
  private void doTest(String filePath) {
    daemonFixture.get().doTest(BASE_PATH + "/" + filePath, false, false);
  }
}
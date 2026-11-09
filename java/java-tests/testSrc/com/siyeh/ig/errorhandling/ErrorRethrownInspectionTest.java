/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.errorhandling;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ErrorRethrownInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ErrorRethrownInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (Error /*Error 'e' not rethrown*/e/**/) {" +
                    "  e.printStackTrace();" +
                    "}");
  }

  @Test
  public void testNoWarn1() {
    inspectionFixture.get().doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (Error e) {" +
                    "  e.printStackTrace();" +
                    "  throw e;" +
                    "}");
  }

  @Test
  public void testNoWarn2() {
    inspectionFixture.get().doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (AssertionError e) {" +
                    "  e.printStackTrace();" +
                    "  throw e;" +
                    "}");
  }

  @Test
  public void testSubclass() {
    inspectionFixture.get().doStatementTest("try {" +
                    "  System.out.println(\"foo\");" +
                    "} catch (AssertionError /*Error 'e' not rethrown*/e/**/) {" +
                    "  e.printStackTrace();" +
                    "}");
  }
}

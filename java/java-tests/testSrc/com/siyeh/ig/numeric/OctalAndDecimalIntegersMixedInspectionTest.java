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
package com.siyeh.ig.numeric;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class OctalAndDecimalIntegersMixedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new OctalAndDecimalIntegersMixedInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doStatementTest("int[] is = /*Octal and decimal integers in the same array initializer*/{34, 987, (007), 661}/**/;");
  }

  @Test
  public void testBinaryLiteral() {
    inspectionFixture.get().doStatementTest("int[] elapsed = {1, 13, 0b11001};");
  }

  @Test
  public void testZeroesAllowed() {
    inspectionFixture.get().doStatementTest("long[] ns =  new long[] {033L, 0L, 025L};");
  }

  @Test
  public void testZeroesAllowed2() {
    inspectionFixture.get().doStatementTest("int[] ns =  new int[] {033, 0, 025};");
  }
}
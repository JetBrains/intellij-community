/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ObjectToStringInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  private ObjectToStringInspection myInspection;

  @Test
  public void testObjectToString() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testObjectToString_IGNORE_TOSTRING() {
    myInspection.IGNORE_TOSTRING = true;
    inspectionFixture.get().doTest();
  }

  @Test
  public void testObjectToString_IGNORE_EXCEPTION() {
    myInspection.IGNORE_EXCEPTION = true;
    inspectionFixture.get().doTest();
  }

  @Test
  public void testObjectToString_IGNORE_ASSERT() {
    myInspection.IGNORE_ASSERT = true;
    inspectionFixture.get().doTest();
  }

  @Test
  public void testObjectToString_IGNORE_NONNLS() {
    myInspection.IGNORE_NONNLS = true;
    inspectionFixture.get().doTest();
  }

  private @NotNull InspectionProfileEntry getInspection() {
    myInspection = new ObjectToStringInspection();
    return myInspection;
  }
}

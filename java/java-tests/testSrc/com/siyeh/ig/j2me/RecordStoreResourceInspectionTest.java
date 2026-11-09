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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class RecordStoreResourceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

  @Test
  public void testRecordStoreResource() {
    inspectionFixture.get().doTest();
  }

  private static String[] getEnvironmentClasses() {
    return new String[]{
      "package javax.microedition.rms;" +
      "public class RecordStore {" +
      "  public byte[] getRecord(int recordId) {" +
      "    return null;" +
      "  }" +
      "  public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary) {" +
      "    return null;" +
      "  }" +
      "  public void closeRecordStore() {}" +
      "}"
    };
  }

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final RecordStoreResourceInspection inspection = new RecordStoreResourceInspection();
    inspection.insideTryAllowed = true;
    return inspection;
  }
}
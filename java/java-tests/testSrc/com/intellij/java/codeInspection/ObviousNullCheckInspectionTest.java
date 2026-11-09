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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ObviousNullCheckInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ObviousNullCheckInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8_ANNOTATED);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ObviousNullCheckInspection());

  @BeforeEach
  void setUpTestDataPath() {
    inspectionFixture.get().getFixture().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_DIR);
  }

  static final String TEST_DATA_DIR = "/inspection/obviousNotNull/";

  @Test
  public void testObviousNullCheck() {
    inspectionFixture.get().doTest();
  }

  public static class ObviousNullCheckInspectionFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new ObviousNullCheckInspection()};
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
      return LightJavaCodeInsightFixtureTestCase.JAVA_11_ANNOTATED;
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_DIR;
    }
  }
}

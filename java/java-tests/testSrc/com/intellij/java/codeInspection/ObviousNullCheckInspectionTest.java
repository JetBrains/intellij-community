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
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ObviousNullCheckInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class ObviousNullCheckInspectionTest extends LightJavaInspectionTestCase {
  static final String TEST_DATA_DIR = "/inspection/obviousNotNull/";

  public void testObviousNullCheck() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ObviousNullCheckInspection();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + TEST_DATA_DIR;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  public static class ObviousNullCheckInspectionFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new ObviousNullCheckInspection()};
    }

    @NotNull
    @Override
    protected LightProjectDescriptor getProjectDescriptor() {
      return JAVA_11_ANNOTATED;
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_DIR;
    }
  }
}

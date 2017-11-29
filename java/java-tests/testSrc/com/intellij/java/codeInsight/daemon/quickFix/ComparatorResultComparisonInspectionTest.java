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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ComparatorResultComparisonInspection;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;


public class ComparatorResultComparisonInspectionTest extends LightInspectionTestCase {
  static final String TEST_DATA_DIR = "/codeInsight/daemonCodeAnalyzer/quickFix/comparatorResultComparison/";

  public void testComparatorResultComparison() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ComparatorResultComparisonInspection();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + TEST_DATA_DIR;
  }

  public static class ComparatorResultComparisonInspectionFixTest extends LightQuickFixParameterizedTestCase {
    @NotNull
    @Override
    protected LocalInspectionTool[] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new ComparatorResultComparisonInspection()};
    }

    public void test() {
      doAllTests();
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_DIR;
    }
  }
}

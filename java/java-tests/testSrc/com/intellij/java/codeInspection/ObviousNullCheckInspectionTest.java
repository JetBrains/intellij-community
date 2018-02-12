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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
public class ObviousNullCheckInspectionTest extends LightInspectionTestCase {
  static final String TEST_DATA_DIR = "/inspection/obviousNotNull/";

  private static final LightProjectDescriptor JAVA_8_WITH_ANNOTATIONS = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk18());
    }
  };

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
    return JAVA_8_WITH_ANNOTATIONS;
  }

  public static class ObviousNullCheckInspectionFixTest extends LightQuickFixParameterizedTestCase {
    @NotNull
    @Override
    protected LocalInspectionTool[] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new ObviousNullCheckInspection()};
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

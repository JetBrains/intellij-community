/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 *         Date: 2/15/12
 */
public class GlobalInspectionsTest extends CodeInsightFixtureTestCase {

  public void testUnusedDeclarationInspection() throws Exception {
    InspectionProfileImpl profile = (InspectionProfileImpl)InspectionProfileManager.getInstance().createProfile();
    try {
      InspectionProfileImpl.INIT_INSPECTIONS = true;
      profile.initInspectionTools(getProject());
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
    InspectionProfileEntry tool = profile.getInspectionTool(new UnusedDeclarationInspection().getShortName());
    myFixture.testInspection("unusedDeclaration", (InspectionTool)tool);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/global/";
  }

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public GlobalInspectionsTest() {
    IdeaTestCase.initPlatformPrefix();
  }
}

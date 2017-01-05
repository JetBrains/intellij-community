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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.testFramework.InspectionTestCase;

public abstract class AbstractUnusedDeclarationTest extends InspectionTestCase {
  protected UnusedDeclarationInspection myTool;
  protected GlobalInspectionToolWrapper myToolWrapper;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myToolWrapper = getUnusedDeclarationWrapper();
    myTool = (UnusedDeclarationInspection)myToolWrapper.getTool();
  }

  protected void doTest() {
    myTool.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    myTool.getSharedLocalInspectionTool().PARAMETER = false;
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }
}

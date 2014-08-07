/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author lambdamix
 */
public class BytecodeAnalysisResultsHighlightingTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setUpLibraries();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInspection/bytecodeAnalysis/src/";
  }

  private void doTest() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    inspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testExample() {
    doTest();
  }

  private void setUpLibraries() {
    VirtualFile lib = LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManagerEx.getTestDataPath() + "/../../../lib");
    assertNotNull(lib);
    PsiTestUtil.addLibrary(myModule, "velocity", lib.getPath(), new String[]{"/velocity.jar!/"}, new String[]{});
  }
}

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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.testOnly.TestOnlyInspection;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

public class TestOnlyInspectionTest extends InspectionTestCase {
  @Override
  protected void setupRootModel(String testDir, VirtualFile[] sourceDir, String jdkName) {
    super.setupRootModel(testDir, sourceDir, jdkName);
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(testDir);
    VirtualFile test = projectDir.findChild("test");
    if (test != null) PsiTestUtil.addSourceRoot(myModule, test, true);
  }

  @NotNull
  @Override
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    return new AnalysisScope(myModule);
  }

  public void testSimple() throws Exception {
    doTest();
  }

  public void testInsideInner() throws Exception {
    doTest();
  }

  public void testConstructor() throws Exception {
    doTest();
  }

  public void testVisibleForTesting() throws Exception { doTest(); }

  public void testUnresolved() throws Exception {
    doTest(); // shouldn't throw
  }
  
  public void testClass() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    TestOnlyInspection i = new TestOnlyInspection();
    doTest("testOnly/" + getTestName(true), new LocalInspectionToolWrapper(i), "java 1.5");
  }
}
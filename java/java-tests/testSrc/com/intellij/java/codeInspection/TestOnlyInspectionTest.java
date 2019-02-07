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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.testOnly.TestOnlyInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class TestOnlyInspectionTest extends InspectionTestCase {

  private final static DefaultLightProjectDescriptor ourProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      contentEntry.addSourceFolder(contentEntry.getUrl() + "/test", true);
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourProjectDescriptor;
  }

  @NotNull
  @Override
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    return new AnalysisScope(myModule);
  }

  public void testSimple() {
    doTest();
  }

  public void testInsideInner() {
    doTest();
  }

  public void testConstructor() {
    doTest();
  }

  public void testVisibleForTesting() { doTest(); }

  public void testUnresolved() {
    doTest(); // shouldn't throw
  }
  
  public void testClass() {
    doTest();
  }
  
  public void testInsideField() {
    doTest();
  }

  private void doTest() {
    TestOnlyInspection i = new TestOnlyInspection();
    doTest("testOnly/" + getTestName(true), new LocalInspectionToolWrapper(i));
  }
}
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
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.unusedLibraries.UnusedLibrariesInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class UnusedLibraryInspectionTest extends JavaInspectionTestCase {

  private final DefaultLightProjectDescriptor myProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      PsiTestUtil.addProjectLibrary(model, "JUnit", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit4"));
      if (getTestName(true).endsWith("Runtime")) {
        for (OrderEntry entry : model.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry && "JUnit".equals(((LibraryOrderEntry)entry).getLibraryName())) {
            ((LibraryOrderEntry)entry).setScope(DependencyScope.RUNTIME);
          }
        }
      }
    }
  };

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/unusedLibrary";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return myProjectDescriptor;
  }

  private void doTest() {
    doTest("/" + getTestName(true), new UnusedLibrariesInspection());
  }

  @NotNull
  @Override
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    return new AnalysisScope(getProject());
  }

  public void testSimple() { doTest(); }
  public void testUsedJunit() { doTest(); }
  public void testJunitAsRuntime() { doTest(); }
  public void testUsedJunitFromField() { doTest(); }
  public void testUsedInParameterAnnotation() { doTest(); }
}

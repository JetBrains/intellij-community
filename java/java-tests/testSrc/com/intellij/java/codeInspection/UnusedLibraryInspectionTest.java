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
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixtureWithProjectPerTest;

@TestApplication
public class UnusedLibraryInspectionTest {
  private static final LightProjectDescriptor DESCRIPTOR = new JUnitLibraryDescriptor(false);
  private static final LightProjectDescriptor RUNTIME_DESCRIPTOR = new JUnitLibraryDescriptor(true);
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixtureWithProjectPerTest(
    testName -> testName.endsWith("Runtime") ? RUNTIME_DESCRIPTOR : DESCRIPTOR,
    JavaTestUtil.getJavaTestDataPath() + "/inspection/unusedLibrary", (project, ignored) -> new AnalysisScope(project));

  /**
   * The JUnit project library. With {@code runtime}, the module has it in the runtime scope.
   */
  private static final class JUnitLibraryDescriptor extends DefaultLightProjectDescriptor {
    private final boolean myRuntime;

    private JUnitLibraryDescriptor(boolean runtime) {
      myRuntime = runtime;
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      PsiTestUtil.addProjectLibrary(model, "JUnit", IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit4", "JUnit4").getClassesPaths());
      if (myRuntime) {
        for (OrderEntry entry : model.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry && "JUnit".equals(((LibraryOrderEntry)entry).getLibraryName())) {
            ((LibraryOrderEntry)entry).setScope(DependencyScope.RUNTIME);
          }
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof JUnitLibraryDescriptor descriptor && myRuntime == descriptor.myRuntime;
    }

    @Override
    public int hashCode() {
      return Boolean.hashCode(myRuntime);
    }
  }

  private void doTest() {
    javaInspection.get().doTest("/" + javaInspection.get().getTestName(true), new UnusedLibrariesInspection());
  }

  @Test
  public void testSimple() { doTest(); }
  @Test
  public void testUsedJunit() { doTest(); }
  @Test
  public void testJunitAsRuntime() { doTest(); }
  @Test
  public void testUsedJunitFromField() { doTest(); }
  @Test
  public void testUsedInParameterAnnotation() { doTest(); }
}

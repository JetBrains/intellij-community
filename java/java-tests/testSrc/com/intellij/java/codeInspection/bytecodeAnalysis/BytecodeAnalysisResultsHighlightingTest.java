// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.JavaTestUtil;
import com.intellij.java.codeInspection.DataFlowInspectionTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class BytecodeAnalysisResultsHighlightingTest extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        PsiTestUtil.addProjectLibrary(model, "velocity", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("Velocity"));
        PsiTestUtil.addProjectLibrary(model, "commons-lang", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("commons-lang"));
      }
    };
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInspection/bytecodeAnalysis/src/";
  }

  public void testExample() { doTest(); }
}
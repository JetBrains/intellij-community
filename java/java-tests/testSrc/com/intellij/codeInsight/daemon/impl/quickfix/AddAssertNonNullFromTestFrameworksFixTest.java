// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class AddAssertNonNullFromTestFrameworksFixTest extends LightQuickFixParameterizedTestCase {
  private static final LightProjectDescriptor ourProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);

      IntelliJProjectConfiguration.LibraryRoots junit3Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit3");
      PsiTestUtil.addLibrary(model, "JUnit3", "", ArrayUtil.toStringArray(junit3Library.getClassesPaths()));

      IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit4");
      PsiTestUtil.addLibrary(model, "JUnit4", "", ArrayUtil.toStringArray(junit4Library.getClassesPaths()));

      IntelliJProjectConfiguration.LibraryRoots junit5Library = IntelliJProjectConfiguration.getProjectLibrary("JUnit5");
      PsiTestUtil.addLibrary(model, "JUnit5", "", ArrayUtil.toStringArray(junit5Library.getClassesPaths()));

      IntelliJProjectConfiguration.LibraryRoots testNGLibrary = IntelliJProjectConfiguration.getProjectLibrary("TestNG");
      PsiTestUtil.addLibrary(model, "TestNG", "", ArrayUtil.toStringArray(testNGLibrary.getClassesPaths()));

      DefaultLightProjectDescriptor.addJetBrainsAnnotations(model);
    }
  };

  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return ourProjectDescriptor;
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DataFlowInspection()};
  }

  @Override
  protected @NotNull String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addAssertNonNullFromTestFrameworks";
  }
}

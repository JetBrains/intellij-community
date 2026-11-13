// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.bytecodeAnalysis;

import com.intellij.JavaTestUtil;
import com.intellij.java.codeInspection.DataFlowInspectionFixture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class BytecodeAnalysisResultsHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      DefaultLightProjectDescriptor.addJetBrainsAnnotations(model);
      PsiTestUtil.addProjectLibrary(model, "velocity", IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.velocity", "Velocity").getClassesPaths());
      PsiTestUtil.addProjectLibrary(model, "commons-lang", IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.commons.lang3", "commons-lang3").getClassesPaths());
    }
  });
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInspection/bytecodeAnalysis/src/");

  @Test
  public void testExample() { dataFlow.get().doTest(); }
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class MethodCanBeVariableArityMethodInspectionTest {

  private static final ProjectDescriptor JAVA_HIGHEST_WITH_OLD_ANNOTATIONS = new ProjectDescriptor(LanguageLevel.HIGHEST) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(myLanguageLevel);
      addJetBrainsAnnotations(model);
    }
  };

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_HIGHEST_WITH_OLD_ANNOTATIONS);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testMethodCanBeVariableArity() {
    inspectionFixture.get().doTest();
  }

  private static InspectionProfileEntry getInspection() {
    final MethodCanBeVariableArityMethodInspection inspection = new MethodCanBeVariableArityMethodInspection();
    inspection.ignoreByteAndShortArrayParameters = true;
    inspection.ignoreOverridingMethods = true;
    inspection.onlyReportPublicMethods = true;
    inspection.ignoreMultipleArrayParameters = true;
    return inspection;
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import com.siyeh.ig.LightJavaInspectionFixtureKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Creates the Java fixtures in the IDE test application. It is the default {@link JavaHostFixtures}.
 */
@TestOnly
final class IdeJavaHostFixtures implements JavaHostFixtures {
  @Override
  public @NotNull AnalysisHostTest.Host getHost() {
    return AnalysisHostTest.Host.IDE;
  }

  @Override
  public @NotNull TestFixture<? extends JavaTestProject> projectFixture(@NotNull LightProjectDescriptor descriptor) {
    return LightJavaFixturesKt.lightJavaProjectFixture(descriptor);
  }

  @Override
  public @NotNull TestFixture<? extends JavaInspectionFixture> inspectionFixture(@NotNull TestFixture<? extends JavaTestProject> project,
                                                                                 @NotNull InspectionSpec spec) {
    return LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName(
      LightJavaFixturesKt.lightJavaProject(project), spec.inspection(), spec.environmentClasses().toArray(String[]::new),
      spec.basePath(), spec.inspectionClass(), spec.testDataPath());
  }
}

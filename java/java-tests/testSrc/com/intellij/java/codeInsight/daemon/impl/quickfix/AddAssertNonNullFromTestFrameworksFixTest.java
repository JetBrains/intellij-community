// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class AddAssertNonNullFromTestFrameworksFixTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addAssertNonNullFromTestFrameworks";
  private static final LightProjectDescriptor ourProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);

      com.intellij.testFramework.fixtures.MavenDependencyUtil.addFromMaven(model, "junit:junit:3.8.2");

      IntelliJProjectConfiguration.LibraryRoots junit4Library = IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit4", "JUnit4");
      PsiTestUtil.addLibrary(model, "JUnit4", "", ArrayUtil.toStringArray(junit4Library.getClassesPaths()));

      IntelliJProjectConfiguration.LibraryRoots junit5Library = IntelliJProjectConfiguration.getModuleLibrary("intellij.junit.v5.rt", "JUnit5.rt");
      PsiTestUtil.addLibrary(model, "JUnit5", "", ArrayUtil.toStringArray(junit5Library.getClassesPaths()));

      IntelliJProjectConfiguration.LibraryRoots testNGLibrary = IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.testng", "TestNG");
      PsiTestUtil.addLibrary(model, "TestNG", "", ArrayUtil.toStringArray(testNGLibrary.getClassesPaths()));

      DefaultLightProjectDescriptor.addJetBrainsAnnotations(model);
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(ourProjectDescriptor);
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, () -> List.of(new DataFlowInspection()));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }
}

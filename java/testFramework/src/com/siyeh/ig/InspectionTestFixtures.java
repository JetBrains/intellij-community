// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaHostFixtures;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Creates the {@link JavaInspectionFixture} of a {@link com.intellij.testFramework.junit5.AnalysisHostTest} class
 * on the current {@link JavaHostFixtures}. Declare the fixture in an instance field.
 * <p>
 * The factories mirror {@link LightJavaInspectionFixtureKt#lightJavaInspectionFixture} and
 * {@link LightJavaInspectionFixtureKt#lightJavaInspectionFixtureByTestName}.
 */
@TestOnly
public final class InspectionTestFixtures {
  private InspectionTestFixtures() {
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixture(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Supplier<? extends InspectionProfileEntry> inspection
  ) {
    //noinspection LanguageMismatch
    return javaInspectionFixture(project, inspection, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixture(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Supplier<? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses
  ) {
    return javaInspectionFixture(project, inspection, environmentClasses, null, null, null);
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixture(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Supplier<? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses,
    @Nullable String basePath
  ) {
    return javaInspectionFixture(project, inspection, environmentClasses, basePath, null, null);
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixture(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Supplier<? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses,
    @Nullable String basePath,
    @Nullable Class<? extends InspectionProfileEntry> inspectionClass
  ) {
    return javaInspectionFixture(project, inspection, environmentClasses, basePath, inspectionClass, null);
  }

  /**
   * See {@link JavaHostFixtures.InspectionSpec} for the parameters.
   */
  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixture(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Supplier<? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses,
    @Nullable String basePath,
    @Nullable Class<? extends InspectionProfileEntry> inspectionClass,
    @Nullable String testDataPath
  ) {
    return javaInspectionFixtureByTestName(project, _ -> inspection.get(), environmentClasses, basePath, inspectionClass, testDataPath);
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixtureByTestName(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Function<String, ? extends InspectionProfileEntry> inspection
  ) {
    //noinspection LanguageMismatch
    return javaInspectionFixtureByTestName(project, inspection, ArrayUtil.EMPTY_STRING_ARRAY, null, null, null);
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixtureByTestName(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Function<String, ? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses
  ) {
    return javaInspectionFixtureByTestName(project, inspection, environmentClasses, null, null, null);
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixtureByTestName(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Function<String, ? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses,
    @Nullable String basePath
  ) {
    return javaInspectionFixtureByTestName(project, inspection, environmentClasses, basePath, null, null);
  }

  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixtureByTestName(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Function<String, ? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses,
    @Nullable String basePath,
    @Nullable Class<? extends InspectionProfileEntry> inspectionClass
  ) {
    return javaInspectionFixtureByTestName(project, inspection, environmentClasses, basePath, inspectionClass, null);
  }

  /**
   * See {@link JavaHostFixtures.InspectionSpec} for the parameters.
   */
  public static @NotNull TestFixture<? extends JavaInspectionFixture> javaInspectionFixtureByTestName(
    @NotNull TestFixture<? extends JavaTestProject> project,
    @NotNull Function<String, ? extends InspectionProfileEntry> inspection,
    @Language("JAVA") String @NotNull [] environmentClasses,
    @Nullable String basePath,
    @Nullable Class<? extends InspectionProfileEntry> inspectionClass,
    @Nullable String testDataPath
  ) {
    var spec = new JavaHostFixtures.InspectionSpec(inspection, List.of(environmentClasses), basePath, inspectionClass, testDataPath);
    return JavaHostFixtures.current().inspectionFixture(project, spec);
  }
}

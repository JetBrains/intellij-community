// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Creates the project of a {@link com.intellij.testFramework.junit5.AnalysisHostTest} class on the current {@link JavaHostFixtures}.
 * Declare the project in a static field.
 * <p>
 * The project takes a {@link LightProjectDescriptor}, as a light test of the IDE does.
 * Each host shares the data of equal descriptors.
 * The language server supports {@link LightJavaCodeInsightFixtureTestCase.ProjectDescriptor} and {@link LightJavaCodeInsightFixtureTestCase#JAVA_LATEST}.
 * It takes only the language level of the descriptor, and it uses the JDK that runs the test.
 */
@TestOnly
public final class JavaTestFixtures {
  private JavaTestFixtures() {
  }

  /**
   * Creates the project of {@link LightJavaCodeInsightFixtureTestCase#JAVA_LATEST}, the default descriptor of a light Java test.
   */
  public static @NotNull TestFixture<? extends JavaTestProject> javaTestProjectFixture() {
    return javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST);
  }

  /**
   * Creates the project of {@code descriptor}.
   */
  public static @NotNull TestFixture<? extends JavaTestProject> javaTestProjectFixture(@NotNull LightProjectDescriptor descriptor) {
    return JavaHostFixtures.current().projectFixture(descriptor);
  }
}

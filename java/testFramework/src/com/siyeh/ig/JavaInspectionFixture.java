// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * The helpers of an inspection test that do not depend on the analysis host.
 * Create it with {@link InspectionTestFixtures#javaInspectionFixture}.
 * <p>
 * The current {@link com.intellij.testFramework.fixtures.JavaHostFixtures} implements the helpers.
 * The IDE implementation is {@link LightJavaInspectionFixture}.
 * A helper that the host does not support aborts the test.
 */
@TestOnly
public interface JavaInspectionFixture {
  /**
   * Returns the name of the test method without the {@code test} prefix.
   */
  @NotNull String getTestName(boolean lowercaseFirstLetter);

  void addEnvironmentClass(@Language("JAVA") @NotNull String classText);

  void doStatementTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\") class X { void m() {", suffix = "}}")
                       @NotNull String statementText);

  void doMemberTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\") class X {", suffix = "}") @NotNull String memberText);

  /**
   * Configures {@code X.java} with {@code classText} and checks the highlighting.
   * The comments in {@code classText} mark the expected highlighting, as in {@link LightJavaInspectionTestCase#doTest}.
   */
  void doTest(@Language("JAVA") @NotNull String classText);

  void doTest(@Language("JAVA") @NotNull String classText, @NotNull String fileName);

  /**
   * Configures the file {@code <test name>.java} of the test data and checks the highlighting.
   */
  void doTest();

  void doNamedTest(@NotNull String name);

  void assertQuickFixNotAvailable(@NotNull String name);

  void checkQuickFix(@NotNull String name, @Language("JAVA") @NotNull String result);

  /**
   * Runs the intention {@code name} and compares the file with {@code <test name>.after.java} of the test data.
   */
  void checkQuickFix(@NotNull String name);

  /**
   * Runs the "Fix all" intention of the inspection and compares the file with {@code <test name>.after.java} of the test data.
   */
  void checkQuickFixAll();
}

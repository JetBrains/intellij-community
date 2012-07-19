package org.jetbrains.jps.model.java;

/**
 * @author nik
 */
public enum JpsJavaClasspathKind {
  PRODUCTION_COMPILE, PRODUCTION_RUNTIME,
  TEST_COMPILE, TEST_RUNTIME;

  public boolean isTestsIncluded() {
    return this == TEST_COMPILE || this == TEST_RUNTIME;
  }

  public boolean isRuntime() {
    return this == PRODUCTION_RUNTIME || this == TEST_RUNTIME;
  }

  public static JpsJavaClasspathKind compile(boolean tests) {
    return tests ? TEST_COMPILE : PRODUCTION_COMPILE;
  }

  public static JpsJavaClasspathKind runtime(boolean tests) {
    return tests ? TEST_RUNTIME : PRODUCTION_RUNTIME;
  }
}

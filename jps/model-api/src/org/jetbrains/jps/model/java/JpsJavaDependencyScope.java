package org.jetbrains.jps.model.java;

import java.util.Arrays;
import java.util.EnumSet;

import static org.jetbrains.jps.model.java.JpsJavaClasspathKind.*;

/**
 * @author nik
 */
public enum JpsJavaDependencyScope {
  COMPILE(PRODUCTION_COMPILE, PRODUCTION_RUNTIME, TEST_COMPILE, TEST_RUNTIME),
  TEST(TEST_COMPILE, TEST_RUNTIME),
  RUNTIME(PRODUCTION_RUNTIME, TEST_RUNTIME),
  PROVIDED(PRODUCTION_COMPILE, TEST_COMPILE, TEST_RUNTIME);
  private EnumSet<JpsJavaClasspathKind> myAffectedClasspath;

  JpsJavaDependencyScope(JpsJavaClasspathKind... classpath) {
    myAffectedClasspath = EnumSet.copyOf(Arrays.asList(classpath));
  }

  public boolean isIncludedIn(JpsJavaClasspathKind kind) {
    return myAffectedClasspath.contains(kind);
  }
}

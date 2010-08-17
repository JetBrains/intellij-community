package org.jetbrains.jps

/**
 * @author max
 */
interface ClasspathItem {
  List<String> getClasspathRoots(ClasspathKind kind);
}

enum ClasspathKind {
  PRODUCTION_COMPILE, PRODUCTION_RUNTIME,
  TEST_COMPILE, TEST_RUNTIME;

  boolean isTestsIncluded() {
    return this == TEST_COMPILE || this == TEST_RUNTIME
  }
}

class DependencyScope {
  private Set<ClasspathKind> affectedClasspaths

  DependencyScope(ClasspathKind... kinds) {
    affectedClasspaths = EnumSet.noneOf(ClasspathKind.class)
    affectedClasspaths.addAll(Arrays.asList(kinds))
  }

  boolean isIncludedIn(ClasspathKind kind) {
    return affectedClasspaths.contains(kind)
  }
}

class PredefinedDependencyScopes {
  static DependencyScope COMPILE = new DependencyScope(ClasspathKind.PRODUCTION_COMPILE, ClasspathKind.PRODUCTION_RUNTIME,
                                                ClasspathKind.TEST_COMPILE, ClasspathKind.TEST_RUNTIME)
  static DependencyScope RUNTIME = new DependencyScope(ClasspathKind.PRODUCTION_RUNTIME, ClasspathKind.TEST_RUNTIME)
  static DependencyScope TEST = new DependencyScope(ClasspathKind.TEST_COMPILE, ClasspathKind.TEST_RUNTIME)
  static DependencyScope PROVIDED = new DependencyScope(ClasspathKind.PRODUCTION_COMPILE, ClasspathKind.TEST_COMPILE, ClasspathKind.TEST_RUNTIME)
}
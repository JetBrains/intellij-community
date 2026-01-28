package com.intellij.tools.build.bazel;

import org.junit.jupiter.api.Nested;

public final class AllTests {
  @Nested
  class IncrementalBuildTests extends JvmIncBuilderTest {}

  @Nested
  class ZipTests extends ZipBuilderTest {}

  @Nested
  class DependencyGraphTests extends DependencyGraphTest {}
}

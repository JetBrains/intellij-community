// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.ContentModuleName
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.validator.computeImplicitDeps
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Tests for [computeImplicitDeps] function in the graph DSL.
 *
 * The `computeImplicitDeps` function computes JPS dependencies that are missing from XML
 * (JPS deps from EDGE_TARGET_DEPENDS_ON via backedBy traversal, excluding those present in
 * EDGE_CONTENT_MODULE_DEPENDS_ON). This is used by Layer 4 validation to identify
 * JPS deps filtered out from XML generation.
 */
@ExtendWith(TestFailureLogger::class)
class ComputeImplicitDepsTest {

  @Nested
  inner class TestScopeFilteringTest {
    /**
     * Verifies that TEST scope JPS dependencies are excluded from implicit deps computation.
     *
     * Bug fix: Previously, computeImplicitDeps() compared ALL JPS deps vs production XML deps,
     * causing TEST scope deps to appear as "filtered by config" false positives.
     *
     * The fix filters out TEST scope deps when traversing EDGE_TARGET_DEPENDS_ON edges.
     */
    @Test
    fun `TEST scope JPS dependencies are excluded from implicit deps`() {
      // Setup: Module with both COMPILE and TEST scope JPS deps
      // Only COMPILE dep is in XML (as expected for production)
      val graph = pluginGraph {
        // Create content module with scoped JPS deps
        moduleWithScopedDeps(
          "my.module",
          "compile.dep" to "COMPILE",  // Production dep - in XML
          "test.dep" to "TEST",         // Test dep - NOT in XML (expected)
        )

        // Create the dependency modules so classifyTarget can find them
        moduleWithDeps("compile.dep")
        moduleWithDeps("test.dep")

        // Add XML dependency only for compile.dep (production)
        linkContentModuleDeps("my.module", "compile.dep")
        // Note: test.dep is intentionally NOT in XML deps
      }

      // Execute: Compute implicit deps
      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      // Verify: test.dep should NOT appear as implicit (filtered) dep
      // because it's TEST scope and we only compare production deps
      assertThat(implicitDeps)
        .describedAs("TEST scope deps should be excluded from implicit deps")
        .doesNotContain(ContentModuleName("test.dep"))

      // Verify: compile.dep should also NOT appear because it's in both JPS and XML
      assertThat(implicitDeps)
        .describedAs("COMPILE dep that's in both JPS and XML should not be implicit")
        .doesNotContain(ContentModuleName("compile.dep"))

      // Verify: No implicit deps at all in this case
      assertThat(implicitDeps).isEmpty()
    }

    @Test
    fun `COMPILE scope JPS dependency missing from XML appears as implicit`() {
      // Setup: Module with COMPILE scope JPS dep that's NOT in XML
      val graph = pluginGraph {
        moduleWithScopedDeps(
          "my.module",
          "missing.dep" to "COMPILE",  // Production dep - but NOT in XML
        )
        moduleWithDeps("missing.dep")
        // Note: No linkContentModuleDeps - the dep is missing from XML
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      // Verify: COMPILE scope dep missing from XML should appear as implicit
      assertThat(implicitDeps)
        .describedAs("COMPILE scope dep missing from XML should be implicit")
        .contains(ContentModuleName("missing.dep"))
    }

    @Test
    fun `RUNTIME scope JPS dependency missing from XML appears as implicit`() {
      // Setup: Module with RUNTIME scope JPS dep that's NOT in XML
      val graph = pluginGraph {
        moduleWithScopedDeps(
          "my.module",
          "runtime.dep" to "RUNTIME",
        )
        moduleWithDeps("runtime.dep")
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      // Verify: RUNTIME scope dep should appear as implicit (not filtered like TEST)
      assertThat(implicitDeps)
        .describedAs("RUNTIME scope dep missing from XML should be implicit")
        .contains(ContentModuleName("runtime.dep"))
    }

    @Test
    fun `mixed scopes - only non-TEST missing deps are implicit`() {
      // Setup: Module with multiple JPS deps of different scopes
      val graph = pluginGraph {
        moduleWithScopedDeps(
          "my.module",
          "compile.present" to "COMPILE",   // In XML
          "compile.missing" to "COMPILE",   // Not in XML -> implicit
          "test.missing" to "TEST",         // Not in XML -> NOT implicit (TEST scope)
          "runtime.missing" to "RUNTIME",   // Not in XML -> implicit
        )

        moduleWithDeps("compile.present")
        moduleWithDeps("compile.missing")
        moduleWithDeps("test.missing")
        moduleWithDeps("runtime.missing")

        // Only compile.present is in XML
        linkContentModuleDeps("my.module", "compile.present")
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      // Verify: Only non-TEST missing deps should be implicit
      assertThat(implicitDeps)
        .containsExactlyInAnyOrder(
          ContentModuleName("compile.missing"),
          ContentModuleName("runtime.missing"),
        )

      // Verify: TEST scope dep is excluded
      assertThat(implicitDeps)
        .doesNotContain(ContentModuleName("test.missing"))

      // Verify: Present dep is not implicit
      assertThat(implicitDeps)
        .doesNotContain(ContentModuleName("compile.present"))
    }

    /**
     * Verifies that PROVIDED scope JPS dependencies are excluded from implicit deps computation.
     *
     * PROVIDED scope means "provided by runtime environment" (e.g., platform APIs, JDK classes).
     * These are needed at compile time but not at runtime, so they should NOT be written to XML.
     *
     * See JpsJavaDependencyScope.java - PROVIDED is in PRODUCTION_COMPILE but NOT PRODUCTION_RUNTIME.
     */
    @Test
    fun `PROVIDED scope JPS dependencies are excluded from implicit deps`() {
      // Setup: Module with PROVIDED scope JPS dep that's NOT in XML (as expected)
      val graph = pluginGraph {
        moduleWithScopedDeps(
          "my.module",
          "compile.dep" to "COMPILE",     // Production dep - in XML
          "provided.dep" to "PROVIDED",   // Provided by environment - NOT in XML (expected)
        )

        moduleWithDeps("compile.dep")
        moduleWithDeps("provided.dep")

        // Add XML dependency only for compile.dep (production)
        linkContentModuleDeps("my.module", "compile.dep")
        // Note: provided.dep is intentionally NOT in XML deps
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      // Verify: provided.dep should NOT appear as implicit (filtered) dep
      // because it's PROVIDED scope and we only compare PRODUCTION_RUNTIME deps
      assertThat(implicitDeps)
        .describedAs("PROVIDED scope deps should be excluded from implicit deps")
        .doesNotContain(ContentModuleName("provided.dep"))

      // Verify: No implicit deps at all in this case
      assertThat(implicitDeps).isEmpty()
    }

    @Test
    fun `mixed scopes with PROVIDED - only COMPILE and RUNTIME missing deps are implicit`() {
      // Setup: Module with all JPS scope types
      val graph = pluginGraph {
        moduleWithScopedDeps(
          "my.module",
          "compile.present" to "COMPILE",     // In XML
          "compile.missing" to "COMPILE",     // Not in XML -> implicit
          "runtime.missing" to "RUNTIME",     // Not in XML -> implicit
          "provided.missing" to "PROVIDED",   // Not in XML -> NOT implicit (PROVIDED scope)
          "test.missing" to "TEST",           // Not in XML -> NOT implicit (TEST scope)
        )

        moduleWithDeps("compile.present")
        moduleWithDeps("compile.missing")
        moduleWithDeps("runtime.missing")
        moduleWithDeps("provided.missing")
        moduleWithDeps("test.missing")

        linkContentModuleDeps("my.module", "compile.present")
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      // Verify: Only COMPILE and RUNTIME missing deps should be implicit
      // (matches PRODUCTION_RUNTIME semantics)
      assertThat(implicitDeps)
        .containsExactlyInAnyOrder(
          ContentModuleName("compile.missing"),
          ContentModuleName("runtime.missing"),
        )

      // Verify: PROVIDED and TEST scope deps are excluded
      assertThat(implicitDeps)
        .doesNotContain(ContentModuleName("provided.missing"))
        .doesNotContain(ContentModuleName("test.missing"))
    }
  }

  @Nested
  inner class BasicFunctionalityTest {
    @Test
    fun `returns empty set for module with no JPS deps`() {
      val graph = pluginGraph {
        moduleWithDeps("lonely.module")
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("lonely.module"))
      }

      assertThat(implicitDeps).isEmpty()
    }

    @Test
    fun `returns empty set for nonexistent module`() {
      val graph = pluginGraph {
        moduleWithDeps("existing.module")
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("nonexistent.module"))
      }

      assertThat(implicitDeps).isEmpty()
    }

    @Test
    fun `self JPS dependency is excluded from implicit deps`() {
      val graph = pluginGraph {
        moduleWithScopedDeps("my.module", "my.module" to "COMPILE")
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      assertThat(implicitDeps)
        .describedAs("Self dependencies should not be treated as implicit deps")
        .doesNotContain(ContentModuleName("my.module"))
        .isEmpty()
    }

    @Test
    fun `XML dep not in JPS is not implicit`() {
      // Setup: Module with XML dep that's not in JPS
      val graph = pluginGraph {
        moduleWithDeps("my.module")  // No JPS deps
        moduleWithDeps("extra.xml.dep")

        // Add XML dep that's not backed by JPS
        linkContentModuleDeps("my.module", "extra.xml.dep")
      }

      val implicitDeps = graph.query {
        computeImplicitDeps(ContentModuleName("my.module"))
      }

      // Verify: XML dep not in JPS is an explicit declaration, not an implicit filtered dep
      assertThat(implicitDeps)
        .describedAs("XML deps without JPS backing should not be treated as implicit")
        .isEmpty()
    }
  }
}

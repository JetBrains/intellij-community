// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator.rule

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Unit tests for validation rules in PluginDependencyResolution.kt.
 * Tests rules in isolation without full integration context.
 */
@ExtendWith(TestFailureLogger::class)
class PluginDependencyResolutionTest {
  @Nested
  inner class IsTestPluginTest {
    @Test
    fun `detects test plugin by testFramework suffix`() {
      val result = isTestPlugin(
        pluginTarget = TargetName("intellij.platform.testFramework"),
        contentModules = emptySet(),
        testFrameworkContentModules = emptySet(),
      )
      assertThat(result).isTrue()
    }

    @Test
    fun `detects test plugin by testFramework in middle of name`() {
      val result = isTestPlugin(
        pluginTarget = TargetName("intellij.platform.testFramework.core"),
        contentModules = emptySet(),
        testFrameworkContentModules = emptySet(),
      )
      assertThat(result).isTrue()
    }

    @Test
    fun `detects test plugin by test dot framework pattern`() {
      val result = isTestPlugin(
        pluginTarget = TargetName("some.test.framework.util"),
        contentModules = emptySet(),
        testFrameworkContentModules = emptySet(),
      )
      assertThat(result).isTrue()
    }

    @Test
    fun `detects test plugin by content in testFrameworkContentModules`() {
      val testFrameworkModule = ContentModuleName("intellij.platform.testFramework.core")
      val result = isTestPlugin(
        pluginTarget = TargetName("some.regular.plugin"),
        contentModules = setOf(testFrameworkModule, ContentModuleName("other.module")),
        testFrameworkContentModules = setOf(testFrameworkModule),
      )
      assertThat(result).isTrue()
    }

    @Test
    fun `regular plugin is not test plugin`() {
      val result = isTestPlugin(
        pluginTarget = TargetName("intellij.platform.vcs"),
        contentModules = setOf(ContentModuleName("intellij.platform.vcs.impl")),
        testFrameworkContentModules = setOf(ContentModuleName("intellij.platform.testFramework.core")),
      )
      assertThat(result).isFalse()
    }
  }

  @Nested
  inner class IsStructurallyAllowedTest {
    // EMBEDDED restrictions
    @Test
    fun `EMBEDDED depending on EMBEDDED sibling is allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.EMBEDDED, ModuleLoadingRuleValue.EMBEDDED)).isTrue()
    }

    @Test
    fun `EMBEDDED depending on REQUIRED sibling is NOT allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.EMBEDDED, ModuleLoadingRuleValue.REQUIRED)).isFalse()
    }

    @Test
    fun `EMBEDDED depending on OPTIONAL sibling is NOT allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.EMBEDDED, ModuleLoadingRuleValue.OPTIONAL)).isFalse()
    }

    @Test
    fun `EMBEDDED depending on ON_DEMAND sibling is NOT allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.EMBEDDED, ModuleLoadingRuleValue.ON_DEMAND)).isFalse()
    }

    @Test
    fun `EMBEDDED depending on unspecified sibling is NOT allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.EMBEDDED, null)).isFalse()
    }

    // REQUIRED restrictions - cannot depend on OPTIONAL/ON_DEMAND siblings
    @Test
    fun `REQUIRED depending on EMBEDDED sibling is allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.REQUIRED, ModuleLoadingRuleValue.EMBEDDED)).isTrue()
    }

    @Test
    fun `REQUIRED depending on REQUIRED sibling is allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.REQUIRED, ModuleLoadingRuleValue.REQUIRED)).isTrue()
    }

    @Test
    fun `REQUIRED depending on OPTIONAL sibling is NOT allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.REQUIRED, ModuleLoadingRuleValue.OPTIONAL)).isFalse()
    }

    @Test
    fun `REQUIRED depending on ON_DEMAND sibling is NOT allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.REQUIRED, ModuleLoadingRuleValue.ON_DEMAND)).isFalse()
    }

    @Test
    fun `REQUIRED depending on unspecified sibling is NOT allowed`() {
      // unspecified defaults to OPTIONAL, so this should fail
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.REQUIRED, null)).isFalse()
    }

    // OPTIONAL has no restrictions
    @Test
    fun `OPTIONAL depending on any loading mode is allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.OPTIONAL, ModuleLoadingRuleValue.EMBEDDED)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.OPTIONAL, ModuleLoadingRuleValue.REQUIRED)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.OPTIONAL, ModuleLoadingRuleValue.OPTIONAL)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.OPTIONAL, ModuleLoadingRuleValue.ON_DEMAND)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.OPTIONAL, null)).isTrue()
    }

    // ON_DEMAND has no restrictions
    @Test
    fun `ON_DEMAND depending on any loading mode is allowed`() {
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.ON_DEMAND, ModuleLoadingRuleValue.EMBEDDED)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.ON_DEMAND, ModuleLoadingRuleValue.REQUIRED)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.ON_DEMAND, ModuleLoadingRuleValue.OPTIONAL)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.ON_DEMAND, ModuleLoadingRuleValue.ON_DEMAND)).isTrue()
      assertThat(isStructurallyAllowed(ModuleLoadingRuleValue.ON_DEMAND, null)).isTrue()
    }
  }

  @Nested
  inner class FindStructuralViolationsTest {
    @Test
    fun `structural violations only consider same-plugin sources`() {
      val graph = pluginGraph {
        plugin("test.plugin") { content("mod.required", ModuleLoadingRuleValue.REQUIRED) }
        plugin("other.plugin") { content("mod.optional", ModuleLoadingRuleValue.OPTIONAL) }
      }

      val violations = graph.query {
        val query = createResolutionQuery()
        query.findStructuralViolations(
          pluginName = TargetName("test.plugin"),
          loadingMode = ModuleLoadingRuleValue.REQUIRED,
          deps = setOf(ContentModuleName("mod.optional")),
        )
      }

      assertThat(violations).isEmpty()
    }

    @Test
    fun `structural violations detect same-plugin loading conflicts`() {
      val graph = pluginGraph {
        plugin("test.plugin") {
          content("mod.required", ModuleLoadingRuleValue.REQUIRED)
          content("mod.optional", ModuleLoadingRuleValue.OPTIONAL)
        }
      }

      val violations = graph.query {
        val query = createResolutionQuery()
        query.findStructuralViolations(
          pluginName = TargetName("test.plugin"),
          loadingMode = ModuleLoadingRuleValue.REQUIRED,
          deps = setOf(ContentModuleName("mod.optional")),
        )
      }

      assertThat(violations).containsExactly(ContentModuleName("mod.optional"))
    }
  }

  @Nested
  inner class FindUnresolvedDepsTest {
    @Test
    fun `empty deps returns empty result`() {
      val graph = pluginGraph {}
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(
          deps = emptySet(),
          predicate = existsAnywhere,
          productName = "IDEA",
        )
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `all deps have sources returns empty result`() {
      val graph = pluginGraph {
        product("IDEA") { includesModuleSet("core") }
        moduleSet("core") {
          module("dep.a")
          module("dep.b")
        }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(
          deps = setOf(ContentModuleName("dep.a"), ContentModuleName("dep.b")),
          predicate = existsAnywhere,
          productName = "IDEA",
        )
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `missing dep is returned in result`() {
      val graph = pluginGraph {
        product("IDEA") { includesModuleSet("core") }
        moduleSet("core") { module("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(
          deps = setOf(ContentModuleName("dep.a"), ContentModuleName("dep.missing")),
          predicate = existsAnywhere,
          productName = "IDEA",
        )
      }
      assertThat(result).containsExactly(ContentModuleName("dep.missing"))
    }

    @Test
    fun `allowed missing dep is not returned`() {
      val graph = pluginGraph {}
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(
          deps = setOf(ContentModuleName("dep.missing")),
          predicate = existsAnywhere,
          productName = "IDEA",
          allowedMissing = setOf(ContentModuleName("dep.missing")),
        )
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `dep not in product is returned when using product predicate`() {
      val graph = pluginGraph {
        product("WebStorm") { includesModuleSet("core") }
        moduleSet("core") { module("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(
          deps = setOf(ContentModuleName("dep.a")),
          predicate = forProductionPlugin,
          productName = "IDEA",
        )
      }
      assertThat(result).containsExactly(ContentModuleName("dep.a"))
    }
  }

  @Nested
  inner class PredicateTest {
    @Test
    fun `forProductionPlugin accepts module set in product`() {
      val graph = pluginGraph {
        product("IDEA") { includesModuleSet("core") }
        moduleSet("core") { module("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forProductionPlugin, "IDEA")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `forProductionPlugin rejects module set not in product`() {
      val graph = pluginGraph {
        product("WebStorm") { includesModuleSet("core") }
        moduleSet("core") { module("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forProductionPlugin, "IDEA")
      }
      assertThat(result).containsExactly(ContentModuleName("dep.a"))
    }

    @Test
    fun `forProductionPlugin accepts bundled non-test plugin`() {
      val graph = pluginGraph {
        product("IDEA") { bundlesPlugin("plugin") }
        plugin("plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forProductionPlugin, "IDEA")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `forProductionPlugin accepts test plugin bundled in product`() {
      // Test plugins are accepted in resolution - prod/test boundary is enforced via validation warnings, not resolution
      val graph = pluginGraph {
        product("IDEA") { bundlesTestPlugin("test.plugin") }
        testPlugin("test.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forProductionPlugin, "IDEA")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `forProductionPlugin rejects test plugin not bundled in product`() {
      val graph = pluginGraph {
        product("IDEA") { bundlesPlugin("other") }
        testPlugin("test.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forProductionPlugin, "IDEA")
      }
      assertThat(result).containsExactly(ContentModuleName("dep.a"))
    }

    @Test
    fun `forTestPlugin accepts test plugin`() {
      val graph = pluginGraph {
        product("IDEA") { bundlesTestPlugin("test.plugin") }
        testPlugin("test.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forTestPlugin, "IDEA")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `forDslTestPlugin accepts bundled production plugin`() {
      val graph = pluginGraph {
        product("IDEA") { bundlesPlugin("prod.plugin") }
        plugin("prod.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forDslTestPlugin(TargetName("dsl.test.plugin")), "IDEA")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `forDslTestPlugin accepts additional bundled plugin`() {
      val graph = pluginGraph {
        product("IDEA") { bundlesPlugin("other") }
        plugin("extra.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(
          setOf(ContentModuleName("dep.a")),
          forDslTestPlugin(TargetName("dsl.test.plugin"), setOf(TargetName("extra.plugin"))),
          "IDEA",
        )
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `forDslTestPlugin accepts self test plugin`() {
      val graph = pluginGraph {
        testPlugin("dsl.test.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forDslTestPlugin(TargetName("dsl.test.plugin")), "IDEA")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `forDslTestPlugin rejects other test plugin`() {
      val graph = pluginGraph {
        product("IDEA") { bundlesTestPlugin("other.test.plugin") }
        testPlugin("other.test.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), forDslTestPlugin(TargetName("dsl.test.plugin")), "IDEA")
      }
      assertThat(result).containsExactly(ContentModuleName("dep.a"))
    }

    @Test
    fun `existsAnywhere resolves any source`() {
      val graph = pluginGraph {
        moduleSet("any") { module("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), existsAnywhere, "")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `existsInNonTestSource accepts module set`() {
      val graph = pluginGraph {
        moduleSet("core") { module("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), existsInNonTestSource, "")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `existsInNonTestSource accepts non-test plugin`() {
      val graph = pluginGraph {
        plugin("plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), existsInNonTestSource, "")
      }
      assertThat(result).isEmpty()
    }

    @Test
    fun `existsInNonTestSource rejects test-only plugin`() {
      val graph = pluginGraph {
        testPlugin("test.plugin") { content("dep.a") }
      }
      val result = graph.query {
        val query = createResolutionQuery()
        query.findUnresolvedDeps(setOf(ContentModuleName("dep.a")), existsInNonTestSource, "")
      }
      assertThat(result).containsExactly(ContentModuleName("dep.a"))
    }
  }
}

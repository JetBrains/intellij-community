// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.traversal

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.TargetDependencyScope
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class ModuleDependencyAnalysisTest {
  @Test
  fun `reachability reports satisfied and missing deps via graph`() {
    val graph = pluginGraph {
      moduleSet("set.a") {
        moduleWithDeps("module.a", ModuleLoadingRuleValue.REQUIRED, "module.b", "module.c")
        moduleWithDeps("module.b")
      }
      moduleSet("set.other") {
        moduleWithDeps("module.c")
      }
    }

    val result = checkModuleReachability(ContentModuleName("module.a"), "set.a", graph)

    assertThat(result.error).isNull()
    assertThat(result.satisfied).containsExactlyInAnyOrder(TargetName("module.b"))
    assertThat(result.missing).hasSize(1)
    val missing = result.missing.single()
    assertThat(missing.dependencyName).isEqualTo(TargetName("module.c"))
    assertThat(missing.existsGlobally).isTrue()
    assertThat(missing.foundInModuleSets).containsExactly("set.other")
  }

  @Test
  fun `reachability requires direct module membership`() {
    val graph = pluginGraph {
      moduleSet("parent") {
        nestedSet("child") {
          moduleWithDeps("module.nested")
        }
      }
    }

    val result = checkModuleReachability(ContentModuleName("module.nested"), "parent", graph)

    assertThat(result.error).isEqualTo("Module 'module.nested' is not directly in module set 'parent'")
    assertThat(result.satisfied).isEmpty()
    assertThat(result.missing).isEmpty()
  }

  @Test
  fun `module dependencies include test scope when requested`() {
    val graph = pluginGraph {
      moduleWithScopedDeps(
        "module.a",
        "module.b" to "COMPILE",
        "module.c" to "TEST",
        "module.d" to "RUNTIME",
        "module.e" to "PROVIDED",
      )
    }

    val defaultResult = getModuleDependencies(TargetName("module.a"), graph)

    assertThat(defaultResult.error).isNull()
    assertThat(defaultResult.dependencies).containsExactlyInAnyOrder(
      TargetName("module.b"),
      TargetName("module.d"),
    )
    assertThat(defaultResult.dependencyDetails).containsExactlyInAnyOrder(
      TargetDependencyInfo(TargetName("module.b"), TargetDependencyScope.COMPILE),
      TargetDependencyInfo(TargetName("module.d"), TargetDependencyScope.RUNTIME),
    )

    val testResult = getModuleDependencies(TargetName("module.a"), graph, includeTestDependencies = true)

    assertThat(testResult.dependencies).containsExactlyInAnyOrder(
      TargetName("module.b"),
      TargetName("module.c"),
      TargetName("module.d"),
    )
    assertThat(testResult.dependencyDetails).containsExactlyInAnyOrder(
      TargetDependencyInfo(TargetName("module.b"), TargetDependencyScope.COMPILE),
      TargetDependencyInfo(TargetName("module.c"), TargetDependencyScope.TEST),
      TargetDependencyInfo(TargetName("module.d"), TargetDependencyScope.RUNTIME),
    )
  }

  @Test
  fun `dependency path can include test scoped edges`() {
    val graph = pluginGraph {
      moduleWithScopedDeps("module.a", "module.b" to "TEST")
      moduleWithScopedDeps("module.b", "module.c" to "COMPILE")
    }

    val defaultPath = findDependencyPath(TargetName("module.a"), TargetName("module.c"), graph)
    assertThat(defaultPath.pathExists).isFalse()

    val testPath = findDependencyPath(
      fromModule = TargetName("module.a"),
      toModule = TargetName("module.c"),
      graph = graph,
      includeTestDependencies = true,
    )
    assertThat(testPath.pathExists).isTrue()
    assertThat(testPath.path).containsExactly(
      TargetName("module.a"),
      TargetName("module.b"),
      TargetName("module.c"),
    )
  }

  @Test
  fun `dependency path exposes scopes when requested`() {
    val graph = pluginGraph {
      moduleWithScopedDeps("module.a", "module.b" to "TEST")
      moduleWithScopedDeps("module.b", "module.c" to "COMPILE")
    }

    val scopedPath = findDependencyPath(
      fromModule = TargetName("module.a"),
      toModule = TargetName("module.c"),
      graph = graph,
      includeTestDependencies = true,
      includeScopes = true,
    )

    assertThat(scopedPath.pathExists).isTrue()
    assertThat(scopedPath.pathWithScopes).containsExactly(
      DependencyPathEntry(TargetName("module.a"), null),
      DependencyPathEntry(TargetName("module.b"), TargetDependencyScope.TEST),
      DependencyPathEntry(TargetName("module.c"), TargetDependencyScope.COMPILE),
    )
  }

  @Test
  fun `module owners can include test plugins`() {
    val graph = pluginGraph {
      plugin("intellij.prod.plugin") {
        content("module.a")
      }
      testPlugin("intellij.test.plugin") {
        testContent("module.a")
      }
    }

    val prodOwners = getModuleOwners(ContentModuleName("module.a"), graph)
    assertThat(prodOwners.owners).hasSize(1)
    val prodOwner = prodOwners.owners.single()
    assertThat(prodOwner.name).isEqualTo(TargetName("intellij.prod.plugin"))
    assertThat(prodOwner.isTest).isFalse()

    val allOwners = getModuleOwners(ContentModuleName("module.a"), graph, includeTestSources = true)
    assertThat(allOwners.owners.map { it.name }).containsExactlyInAnyOrder(
      TargetName("intellij.prod.plugin"),
      TargetName("intellij.test.plugin"),
    )
    assertThat(allOwners.owners.any { it.name == TargetName("intellij.test.plugin") && it.isTest }).isTrue()
  }
}

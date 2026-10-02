// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.JpsProjectContext
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Compares [ProductionRuntimeLibraryClosure] with the recursive JPS enumerator for the production runtime classpath.
 */
@ExtendWith(TestFailureLogger::class)
class ProductionRuntimeLibraryClosureTest {
  @Test
  fun `the closure of each module is equal to the JPS enumerator result`(@TempDir tempDir: Path) {
    val jps = createProject(tempDir)
    val modules = jps.project.modules

    // the memo must give the same answer for each query order, because a cycle can start at any member
    for (order in listOf(modules, modules.reversed(), modules.sortedBy { it.name })) {
      val closure = ProductionRuntimeLibraryClosure()
      for (module in order) {
        assertThat(closureLibraryNames(closure, module))
          .describedAs("module %s", module.name)
          .containsExactlyInAnyOrderElementsOf(enumeratorLibraryNames(module))
      }
    }
  }

  @Test
  fun `the closure follows the scope rules`(@TempDir tempDir: Path) {
    val jps = createProject(tempDir)
    val closure = ProductionRuntimeLibraryClosure()

    assertThat(closureLibraryNames(closure, module(jps, "root"))).containsExactlyInAnyOrder(
      "own-lib", "runtime-lib", "exported-lib", "deep-lib", "cycle-a-lib", "cycle-b-lib", "cycle-c-lib",
    )
    // each member of the cycle reaches the libraries of the other members
    for (name in listOf("cycle.a", "cycle.b", "cycle.c")) {
      assertThat(closureLibraryNames(closure, module(jps, name)))
        .describedAs("module %s", name)
        .containsExactlyInAnyOrder("cycle-a-lib", "cycle-b-lib", "cycle-c-lib", "deep-lib")
    }
    assertThat(closureLibraryNames(closure, module(jps, "self.loop"))).containsExactlyInAnyOrder("self-lib")
  }

  @Test
  fun `the last module that reaches a library names it`(@TempDir tempDir: Path) {
    val jps = createProject(tempDir)

    val (checkedModuleCount, violations) = collectMissingLicenseViolations(
      moduleNames = listOf("leaf", "root", "missing.module", "exporter"),
      outputProvider = jps.outputProvider,
      coveredNames = setOf("own-lib", "runtime-lib", "cycle-a-lib", "cycle-b-lib", "cycle-c-lib"),
    )

    assertThat(checkedModuleCount).isEqualTo(3)
    assertThat(violations.map { it.libraryName to it.moduleName }).containsExactlyInAnyOrder(
      "deep-lib" to "exporter",
      "exported-lib" to "exporter",
    )
  }

  private fun createProject(tempDir: Path): JpsProjectContext {
    return jpsProject(tempDir) {
      for (name in listOf(
        "own-lib", "runtime-lib", "test-lib", "provided-lib", "exported-lib", "deep-lib", "test-module-lib", "provided-module-lib",
        "cycle-a-lib", "cycle-b-lib", "cycle-c-lib", "self-lib",
      )) {
        library(name)
      }
      module("leaf") {
        libraryDep("deep-lib")
      }
      module("exporter") {
        libraryDep("exported-lib", exported = true)
        moduleDep("leaf", exported = true)
      }
      module("test.module") {
        libraryDep("test-module-lib")
      }
      module("provided.module") {
        libraryDep("provided-module-lib")
      }
      module("root") {
        libraryDep("own-lib")
        libraryDep("runtime-lib", scope = JpsJavaDependencyScope.RUNTIME)
        libraryDep("test-lib", scope = JpsJavaDependencyScope.TEST)
        libraryDep("provided-lib", scope = JpsJavaDependencyScope.PROVIDED)
        moduleDep("exporter")
        moduleDep("test.module", scope = JpsJavaDependencyScope.TEST)
        moduleDep("provided.module", scope = JpsJavaDependencyScope.PROVIDED)
        moduleDep("cycle.a", scope = JpsJavaDependencyScope.RUNTIME)
      }
      module("cycle.a") {
        libraryDep("cycle-a-lib")
        moduleDep("cycle.b")
      }
      module("cycle.b") {
        libraryDep("cycle-b-lib", exported = true)
        moduleDep("cycle.c", scope = JpsJavaDependencyScope.RUNTIME)
        moduleDep("cycle.a")
      }
      module("cycle.c") {
        libraryDep("cycle-c-lib")
        moduleDep("cycle.b", exported = true)
        moduleDep("leaf")
        moduleDep("test.module", scope = JpsJavaDependencyScope.TEST)
      }
      module("self.loop") {
        libraryDep("self-lib")
        moduleDep("self.loop")
      }
    }
  }
}

private fun module(jps: JpsProjectContext, name: String): JpsModule {
  return jps.project.modules.single { it.name == name }
}

private fun closureLibraryNames(closure: ProductionRuntimeLibraryClosure, module: JpsModule): List<String> {
  val result = ArrayList<String>()
  val indices = closure.libraryIndices(module)
  var index = indices.nextSetBit(0)
  while (index >= 0) {
    result.add(closure.library(index).name)
    index = indices.nextSetBit(index + 1)
  }
  return result
}

private fun enumeratorLibraryNames(module: JpsModule): List<String> {
  return JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
    .libraries
    .map { it.name }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContextImpl
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.SuppressionType
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Isolated unit tests for library module validation.
 *
 * Tests that modules depending on libraries exported by library modules (`intellij.libraries.*`)
 * are detected and fixed to use module dependencies instead.
 *
 * Uses [pluginGraph] DSL for the graph, [jpsProject] DSL for JPS modules,
 * and [runValidationRule] to test through the pipeline interface.
 */
@ExtendWith(TestFailureLogger::class)
class LibraryModuleValidatorTest {

  private val imlContentWithLibraryDep = """
    |<?xml version="1.0" encoding="UTF-8"?>
    |<module type="JAVA_MODULE" version="4">
    |  <component name="NewModuleRootManager">
    |    <orderEntry type="library" scope="TEST" name="JUnit4" level="project" />
    |  </component>
    |</module>
  """.trimMargin()

  @Test
  fun `detects direct library dependencies`(@TempDir tempDir: Path) {
    // Create JPS modules with library dependencies
    val jps = jpsProject(tempDir) {
      library("JUnit4")
      module("intellij.libraries.junit4") {
        libraryDep("JUnit4", exported = true)
      }
      module("test.plugin") {
        baseDir = tempDir
        imlContent = imlContentWithLibraryDep
        libraryDep("JUnit4", scope = JpsJavaDependencyScope.TEST)
      }
    }

    // Create graph with targets and a content module
    val graph = pluginGraph {
      // Targets for JPS modules
      target("intellij.libraries.junit4")
      target("test.plugin")
      // Content module that will be validated
      plugin("test.plugin.parent") {
        content("test.plugin")
      }
    }

    val strategy = DeferredFileUpdater(tempDir)
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, fileUpdater = strategy)
    runBlocking { runValidationRule(LibraryModuleValidator, model) }

    assertThat(strategy.getDiffs())
      .describedAs("Diff should be generated for library dependency violation")
      .hasSize(1)
  }

  @Test
  fun `update suppressions records violations without diffs`(@TempDir tempDir: Path) {
    val jps = jpsProject(tempDir) {
      library("JUnit4")
      module("intellij.libraries.junit4") {
        libraryDep("JUnit4", exported = true)
      }
      module("test.plugin") {
        baseDir = tempDir
        imlContent = imlContentWithLibraryDep
        libraryDep("JUnit4", scope = JpsJavaDependencyScope.TEST)
      }
    }

    val graph = pluginGraph {
      target("intellij.libraries.junit4")
      target("test.plugin")
      plugin("test.plugin.parent") {
        content("test.plugin")
      }
    }

    val strategy = DeferredFileUpdater(tempDir)
    val model = testGenerationModel(
      graph,
      outputProvider = jps.outputProvider,
      fileUpdater = strategy,
      updateSuppressions = true,
    )

    val ctx = ComputeContextImpl(model)
    ctx.initSlot(Slots.LIBRARY_SUPPRESSIONS)
    ctx.initErrorSlot(LibraryModuleValidator.id)
    val nodeCtx = ctx.forNode(LibraryModuleValidator.id)
    runBlocking { LibraryModuleValidator.execute(nodeCtx) }
    ctx.finalizeNodeErrors(LibraryModuleValidator.id)

    assertThat(strategy.getDiffs())
      .describedAs("No diffs should be generated in updateSuppressions mode")
      .isEmpty()

    val suppressions = ctx.tryGet(Slots.LIBRARY_SUPPRESSIONS) ?: emptyList()
    assertThat(suppressions).anySatisfy { usage ->
      assertThat(usage.sourceModule.value).isEqualTo("test.plugin")
      assertThat(usage.suppressedDep).isEqualTo("JUnit4")
      assertThat(usage.type).isEqualTo(SuppressionType.LIBRARY_REPLACEMENT)
    }
  }

  @Test
  fun `generates correct diff replacing library with module dependency`(@TempDir tempDir: Path) {
    // Create JPS modules with library dependencies
    val jps = jpsProject(tempDir) {
      library("JUnit4")
      module("intellij.libraries.junit4") {
        libraryDep("JUnit4", exported = true)
      }
      module("test.plugin") {
        baseDir = tempDir
        imlContent = imlContentWithLibraryDep
        libraryDep("JUnit4", scope = JpsJavaDependencyScope.TEST)
      }
    }

    // Create graph with targets and a content module
    val graph = pluginGraph {
      target("intellij.libraries.junit4")
      target("test.plugin")
      plugin("test.plugin.parent") {
        content("test.plugin")
      }
    }

    val strategy = DeferredFileUpdater(tempDir)
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, fileUpdater = strategy)
    runBlocking { runValidationRule(LibraryModuleValidator, model) }

    val diffs = strategy.getDiffs()
    assertThat(diffs).hasSize(1)
    val diff = diffs.first()

    // actualContent is the current .iml content
    assertThat(diff.actualContent).isEqualTo(imlContentWithLibraryDep)

    // expectedContent is the fixed content
    val fixedContent = diff.expectedContent

    // Must contain module dependency
    assertThat(fixedContent)
      .describedAs("Fixed content must contain module dependency")
      .contains("module-name=\"intellij.libraries.junit4\"")

    // Must NOT contain the library dependency
    assertThat(fixedContent)
      .describedAs("Fixed content must not contain library dependency")
      .doesNotContain("name=\"JUnit4\"")
      .doesNotContain("type=\"library\"")

    // Verify valid XML structure
    val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val document = builder.parse(org.xml.sax.InputSource(java.io.StringReader(fixedContent)))

    val orderEntries = document.getElementsByTagName("orderEntry")
    assertThat(orderEntries.length)
      .describedAs("Should have exactly one orderEntry")
      .isEqualTo(1)

    val orderEntry = orderEntries.item(0) as org.w3c.dom.Element
    assertThat(orderEntry.getAttribute("type")).isEqualTo("module")
    assertThat(orderEntry.getAttribute("module-name")).isEqualTo("intellij.libraries.junit4")
  }

  @Test
  fun `no diff when module has no library violations`(@TempDir tempDir: Path) {
    // Create JPS modules without problematic library dependencies
    val jps = jpsProject(tempDir) {
      module("intellij.clean.module")
    }

    val graph = pluginGraph {
      target("intellij.clean.module")
      plugin("test.plugin") {
        content("intellij.clean.module")
      }
    }

    val strategy = DeferredFileUpdater(tempDir)
    val model = testGenerationModel(graph, outputProvider = jps.outputProvider, fileUpdater = strategy)
    runBlocking { runValidationRule(LibraryModuleValidator, model) }

    assertThat(strategy.getDiffs())
      .describedAs("No diff should be generated when there are no violations")
      .isEmpty()
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.productLayout.ProductContentBuildResult
import org.jetbrains.intellij.build.productLayout.discovery.DiscoveredProduct
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path

/**
 * The drop of the unresolved content modules in the flat descriptor walk, see [walkDescriptors].
 *
 * For a product that sets `skipUnresolvedContentModules`, the walk drops a content module that the JPS model cannot
 * resolve. The generator prints one census line for each dropped module. A product without the flag keeps the module.
 */
class DevDistUnresolvedContentModulesTest {
  @TempDir
  lateinit var projectRoot: Path

  /** A module set member that the synthetic model resolves. */
  private val resolvedSetMember = "intellij.synthetic.resolved"

  /** A module set member that the synthetic model cannot resolve. */
  private val unresolvedSetMember = "intellij.synthetic.unresolved"

  /** A direct content module of the product that the synthetic model cannot resolve. */
  private val unresolvedDirectModule = "intellij.synthetic.direct.unresolved"

  private val unresolvedModules = listOf(unresolvedDirectModule, unresolvedSetMember)

  private class UnresolvedContentProperties(skipUnresolvedContentModules: Boolean) : ProductProperties() {
    init {
      productLayout.skipUnresolvedContentModules = skipUnresolvedContentModules
    }

    override val baseFileName: String = "synthetic"
    override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "synthetic"
    override fun createWindowsCustomizer(projectHome: Path) = null
    override fun createLinuxCustomizer(projectHome: Path) = null
    override fun createMacCustomizer(projectHome: Path) = null
    override fun getProductContentDescriptor() = null
  }

  /** A product whose content is one module set with [resolvedSetMember] and [unresolvedSetMember], and [unresolvedDirectModule]. */
  private fun product(name: String, skipUnresolvedContentModules: Boolean): DiscoveredProduct {
    val set = moduleSet("synthetic.set") {
      module(resolvedSetMember)
      module(unresolvedSetMember)
    }
    return DiscoveredProduct(
      name = name,
      config = ProductConfiguration(modules = emptyList(), className = "Synthetic"),
      properties = UnresolvedContentProperties(skipUnresolvedContentModules),
      spec = productModules {
        moduleSet(set)
        module(unresolvedDirectModule)
      },
      pluginXmlPath = null,
    )
  }

  /** The walk over [products] in a model that holds [resolvedSetMember] alone, and the lines it prints. */
  private fun walk(vararg products: DiscoveredProduct): Pair<DescriptorWalk, List<String>> {
    val project = JpsElementFactory.getInstance().createModel().project
    project.addModule(resolvedSetMember, JpsJavaModuleType.INSTANCE)
    val output = ByteArrayOutputStream()
    val original = System.out
    System.setOut(PrintStream(output, true, Charsets.UTF_8))
    val walk = try {
      walkDescriptors(
        projectRoot = projectRoot,
        outputProvider = SourceRootModuleOutputProvider(project),
        products = products.toList(),
        generatedModuleSetDescriptors = emptyMap(),
      )
    }
    finally {
      System.setOut(original)
    }
    return walk to output.toString(Charsets.UTF_8).lines().filter { it.isNotEmpty() }
  }

  private fun ProductContentBuildResult.moduleNames(): List<String> = contentBlocks.flatMap { it.modules }.map { it.moduleId.name }

  private fun censusLines(lines: List<String>): List<String> = lines.filter { it.startsWith("dropped ") }

  @Test
  fun `a product with the flag drops every unresolved content module and keeps every resolved one`() {
    val (walk, lines) = walk(product("synthetic", skipUnresolvedContentModules = true))

    val content = checkNotNull(walk.contentByProduct.single())
    assertThat(content.moduleNames()).contains(resolvedSetMember).doesNotContainAnyElementsOf(unresolvedModules)
    assertThat(content.moduleToSetChainMapping.keys.map { it.value }).contains(resolvedSetMember).doesNotContainAnyElementsOf(unresolvedModules)
    val census = censusLines(lines)
    assertThat(census).hasSize(unresolvedModules.size)
    for (module in unresolvedModules) {
      assertThat(census).filteredOn { it.startsWith("dropped $module from synthetic:") }.hasSize(1)
    }
  }

  @Test
  fun `the census prints one line for each dropped module and names every product that drops it`() {
    val (_, lines) = walk(product("first", skipUnresolvedContentModules = true), product("second", skipUnresolvedContentModules = true))

    val census = censusLines(lines)
    assertThat(census).hasSize(unresolvedModules.size)
    for (module in unresolvedModules) {
      assertThat(census).filteredOn { it.startsWith("dropped $module from first, second:") }.hasSize(1)
    }
  }

  @Test
  fun `a product without the flag keeps the unresolved content modules and prints no census line`() {
    val (walk, lines) = walk(product("synthetic", skipUnresolvedContentModules = false))

    val content = checkNotNull(walk.contentByProduct.single())
    assertThat(content.moduleNames()).contains(resolvedSetMember).containsAll(unresolvedModules)
    assertThat(content.moduleToSetChainMapping.keys.map { it.value }).contains(unresolvedSetMember)
    assertThat(censusLines(lines)).isEmpty()
  }

  @Test
  fun `the flag of one product does not drop the modules of another product`() {
    val (walk, lines) = walk(product("skipping", skipUnresolvedContentModules = true), product("strict", skipUnresolvedContentModules = false))

    val (skipping, strict) = walk.contentByProduct.map { checkNotNull(it) }
    assertThat(skipping.moduleNames()).doesNotContainAnyElementsOf(unresolvedModules)
    assertThat(strict.moduleNames()).containsAll(unresolvedModules)
    for (module in unresolvedModules) {
      assertThat(censusLines(lines)).filteredOn { it.startsWith("dropped $module from skipping:") }.hasSize(1)
    }
  }
}

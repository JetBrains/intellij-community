// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.DeprecatedXmlInclude
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ModuleSetWithOverrides
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.buildProductContentXml
import org.junit.jupiter.api.Test

private fun module(
  name: String,
  loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL,
  namespace: String? = PluginModuleId.DEFAULT_NAMESPACE,
  requiredIfAvailable: String? = null,
): ContentModule {
  return ContentModule(
    moduleId = PluginModuleId(name, namespace),
    loading = loading,
    requiredIfAvailable = requiredIfAvailable?.let { PluginModuleId(it, PluginModuleId.DEFAULT_NAMESPACE) },
  )
}

private fun spec(
  moduleSets: List<ModuleSetWithOverrides>,
  additionalModules: List<ContentModule> = emptyList(),
  aliases: List<String> = emptyList(),
  includes: List<DeprecatedXmlInclude> = emptyList(),
): ProductModulesContentSpec {
  return ProductModulesContentSpec(
    productModuleAliases = aliases.map(::PluginId),
    deprecatedXmlIncludes = includes,
    moduleSets = moduleSets,
    additionalModules = additionalModules,
  )
}

/** The production rendering of [spec] with the module sets inlined, as the closure walk reads it. */
private fun render(spec: ProductModulesContentSpec): String {
  return buildProductContentXml(
    spec = spec,
    outputProvider = null,
    inlineXmlIncludes = false,
    inlineModuleSets = true,
    metadataBuilder = { it.append("  <id>com.intellij</id>\n") },
  ).xml
}

/** `d` has an alias and a required-if-available member. `b` nests `d`, and `a` nests `b`. */
private val D = ModuleSet(
  name = "d",
  modules = listOf(module("d1", requiredIfAvailable = "x1"), module("d2", ModuleLoadingRuleValue.REQUIRED)),
  alias = PluginId("com.intellij.modules.d"),
)
private val B = ModuleSet(name = "b", modules = listOf(module("b1"), module("b2")), nestedSets = listOf(D))
private val A = ModuleSet(name = "a", modules = listOf(module("a1", ModuleLoadingRuleValue.EMBEDDED), module("a2")), nestedSets = listOf(B))

/** `e` declares no own member, so it yields no row. Its nested set `c` does. */
private val C = ModuleSet(name = "c", modules = listOf(module("c1")))
private val E = ModuleSet(name = "e", modules = emptyList(), nestedSets = listOf(C))

/** `f` already carries a non-default loading, so the quirk leaves its rows alone. `g` nests it. */
private val F = ModuleSet(name = "f", modules = listOf(module("f1", ModuleLoadingRuleValue.REQUIRED), module("f2")))
private val G = ModuleSet(name = "g", modules = listOf(module("g1")), nestedSets = listOf(F))

/**
 * The generator composes a product content over the module-set table as `product_content_rows` and the descriptor writer
 * do. The self-check compares the composition with the Product DSL rendering.
 */
class DevDistProductContentTest {
  @Test
  fun `the composition walks the sets in pre-order and matches the rendering`() {
    val spec = spec(
      moduleSets = listOf(ModuleSetWithOverrides(A), ModuleSetWithOverrides(E)),
      additionalModules = listOf(module("x1"), module("p1", namespace = null), module("x2", ModuleLoadingRuleValue.ON_DEMAND)),
      aliases = listOf("com.intellij.modules.product"),
    )
    val content = productContentPlan(spec, "probe")
    val table = moduleSetRows(spec, "probe")

    assertThat(table.keys).containsExactly(
      "intellij.moduleSets.a", "intellij.moduleSets.b", "intellij.moduleSets.c", "intellij.moduleSets.d", "intellij.moduleSets.e",
    )
    assertThat(table.getValue("intellij.moduleSets.d").loading).containsExactly(entry("d2", "required"))
    assertThat(table.getValue("intellij.moduleSets.d").requiredIfAvailable).containsExactly(entry("d1", "x1"))
    assertThat(table.getValue("intellij.moduleSets.d").alias).isEqualTo("com.intellij.modules.d")
    assertThat(content.privateContentModules).containsExactly("p1")
    assertThat(content.contentModuleLoading).containsExactly(entry("x2", "on-demand"))

    val composed = composeProductContent(content, table)
    assertThat(composed.aliases).containsExactly("com.intellij.modules.d", "com.intellij.modules.product")
    assertThat(composed.blocks.map { block -> block.namespace to block.rows.map { it.name } }).containsExactly(
      "jetbrains" to listOf("a1", "a2", "b1", "b2", "d1", "d2", "c1"),
      "jetbrains" to listOf("x1", "x2"),
      null to listOf("p1"),
    )
    checkProductContent(owner = "probe", content = content, table = table, xml = render(spec))
  }

  @Test
  fun `an override applies to the own members of a top-level set only`() {
    val spec = spec(moduleSets = listOf(ModuleSetWithOverrides(A, mapOf(ContentModuleName("a2") to ModuleLoadingRuleValue.REQUIRED))))
    val content = productContentPlan(spec, "probe")

    assertThat(content.loadingOverrides).containsExactly(entry("a2", "required"))
    val rows = composeProductContent(content, moduleSetRows(spec, "probe")).blocks.single().rows
    assertThat(rows.first { it.name == "a2" }.loading).isEqualTo("required")
    assertThat(rows.first { it.name == "b1" }.loading).isNull()
    checkProductContent(owner = "probe", content = content, table = moduleSetRows(spec, "probe"), xml = render(spec))
  }

  @Test
  fun `a set reached again at top level with overrides updates its earlier rows, as the Kotlin quirk does`() {
    // `a` reaches `b` first. The later top-level `b` overrides `b1`, and no earlier row of `b` carries a loading.
    val spec = spec(moduleSets = listOf(
      ModuleSetWithOverrides(A),
      ModuleSetWithOverrides(B, mapOf(ContentModuleName("b1") to ModuleLoadingRuleValue.EMBEDDED)),
    ))
    val content = productContentPlan(spec, "probe")
    val table = moduleSetRows(spec, "probe")

    val rows = composeProductContent(content, table).blocks.single().rows
    assertThat(rows.map { it.name }).containsExactly("a1", "a2", "b1", "b2", "d1", "d2")
    assertThat(rows.first { it.name == "b1" }.loading).isEqualTo("embedded")
    checkProductContent(owner = "probe", content = content, table = table, xml = render(spec))
  }

  @Test
  fun `the quirk leaves the rows of a set with a non-default loading alone`() {
    val spec = spec(moduleSets = listOf(
      ModuleSetWithOverrides(G),
      ModuleSetWithOverrides(F, mapOf(ContentModuleName("f2") to ModuleLoadingRuleValue.EMBEDDED)),
    ))
    val content = productContentPlan(spec, "probe")
    val table = moduleSetRows(spec, "probe")

    val rows = composeProductContent(content, table).blocks.single().rows
    assertThat(rows.first { it.name == "f2" }.loading).isNull()
    checkProductContent(owner = "probe", content = content, table = table, xml = render(spec))
  }

  @Test
  fun `a table that differs from the DSL fails and names the product and the first row`() {
    val spec = spec(moduleSets = listOf(ModuleSetWithOverrides(A)))
    val content = productContentPlan(spec, "probe")
    val table = moduleSetRows(spec, "probe").toMutableMap()
    val b = table.getValue("intellij.moduleSets.b")
    table.put(b.name, b.copy(modules = b.modules.reversed()))

    assertThatThrownBy { checkProductContent(owner = "The product descriptor of 'probe'", content = content, table = table, xml = render(spec)) }
      .hasMessageContaining("The product descriptor of 'probe'")
      .hasMessageContaining("content row 2")
      .hasMessageContaining("name=b2")
  }

  @Test
  fun `an include keeps its kind and its href, and the parser reads both`() {
    val spec = spec(
      moduleSets = emptyList(),
      includes = listOf(
        DeprecatedXmlInclude(ContentModuleName("intellij.x"), "META-INF/x.xml"),
        DeprecatedXmlInclude(ContentModuleName("intellij.y"), "intellij.y.xml", optional = true),
      ),
    )

    assertThat(productContentPlan(spec, "probe").includes).containsExactly(
      entry("/META-INF/x.xml", "required"),
      entry("intellij.y.xml", "optional"),
    )
    val parsed = parseProductContent(
      """
      <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
        <id>com.intellij</id>
        <module value="com.intellij.modules.b"/>
        <xi:include href="/META-INF/x.xml"/>
        <xi:include href="intellij.y.xml"><xi:fallback/></xi:include>
      </idea-plugin>
      """.trimIndent()
    )
    assertThat(parsed.aliases).containsExactly("com.intellij.modules.b")
    assertThat(parsed.includes).containsExactly("required=/META-INF/x.xml", "optional=intellij.y.xml")
  }

  @Test
  fun `the attributes leave out an empty value and name the table`() {
    val attributes = productContentPlan(spec(moduleSets = listOf(ModuleSetWithOverrides(C))), "probe").attributes(DEV_DIST_MODULE_SETS_SYMBOL)

    assertThat(attributes.keys).containsExactly("module_set_table", "module_sets")
    assertThat(formatValue(attributes.getValue("module_set_table"))).isEqualTo("DEV_DIST_MODULE_SETS")
    assertThat(formatValue(attributes.getValue("module_sets"))).isEqualTo("[\"intellij.moduleSets.c\"]")
  }
}

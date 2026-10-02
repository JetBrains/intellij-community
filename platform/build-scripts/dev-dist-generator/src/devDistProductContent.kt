// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.xmlValue
import org.jetbrains.intellij.build.isModuleNameLikeFilename
import org.jetbrains.intellij.build.productLayout.ContentModule
import org.jetbrains.intellij.build.productLayout.MODULE_SET_PREFIX
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import java.util.TreeMap

/**
 * The content of one product descriptor, as the attributes of `dev_dist_product_descriptor` and
 * `dev_dist_embedded_product_descriptor` state it. The module-set table of the half, see [ModuleSetData], states the
 * rest.
 *
 * The macro walks the table with `product_content_rows` of `dev_dist_product_content.bzl`. The descriptor writer composes
 * the `<idea-plugin>` element from the rows, as `buildProductContentXml(inlineModuleSets = true)` renders it.
 */
internal data class ProductContentPlan(
  /** The product aliases, `productModuleAliases`, in DSL order. The macro adds the set aliases and sorts them. */
  @JvmField val aliases: List<String>,
  /** The deprecated includes: the `xi:include` href to `required` or `optional`, in DSL order. */
  @JvmField val includes: Map<String, String>,
  /** The top-level module sets, `intellij.moduleSets.<name>`, in DSL order. */
  @JvmField val moduleSets: List<String>,
  /** The loading rule that a top-level set states for its own member, keyed by the member and sorted. */
  @JvmField val loadingOverrides: Map<String, String>,
  /** The additional modules, in DSL order. */
  @JvmField val contentModules: List<String>,
  /** The additional modules without a namespace, in DSL order. */
  @JvmField val privateContentModules: List<String>,
  /** The non-default loading rule of an additional module, in DSL order. */
  @JvmField val contentModuleLoading: Map<String, String>,
  /** The `required-if-available` module of an additional module, in DSL order. */
  @JvmField val contentModuleRequiredIfAvailable: Map<String, String>,
) {
  /**
   * The macro attributes of this content, keyed by attribute name. A target sorts them with its other attributes, as
   * buildifier does. An empty attribute is left out. [moduleSetTable] is the table the package loads.
   */
  fun attributes(moduleSetTable: String): Map<String, Any> {
    val result = TreeMap<String, Any>()
    aliases.ifNotEmpty { result.put("aliases", it) }
    contentModuleLoading.ifNotEmpty { result.put("content_module_loading", LinkedHashMap(it)) }
    contentModuleRequiredIfAvailable.ifNotEmpty { result.put("content_module_required_if_available", LinkedHashMap(it)) }
    contentModules.ifNotEmpty { result.put("content_modules", it.unsorted()) }
    includes.ifNotEmpty { result.put("includes", LinkedHashMap(it)) }
    loadingOverrides.ifNotEmpty { result.put("loading_overrides", LinkedHashMap(it)) }
    result.put("module_set_table", StarlarkIdentifier(moduleSetTable))
    moduleSets.ifNotEmpty { result.put("module_sets", it.unsorted()) }
    privateContentModules.ifNotEmpty { result.put("private_content_modules", it.unsorted()) }
    return result
  }
}

/** A name that a `load` line binds, rendered as it is. */
internal class StarlarkIdentifier(private val name: String) : Renderable {
  override fun render(): String = name
}

/** The symbol of the module-set table, which a product descriptor package loads from [DEV_DIST_MODULE_SETS_BZL]. */
internal const val DEV_DIST_MODULE_SETS_SYMBOL: String = "DEV_DIST_MODULE_SETS"

/** The module-set table of the half, in the spelling of a package of that half. */
internal const val DEV_DIST_MODULE_SETS_BZL: String = "//build:dev_dist_module_sets.bzl"

/** The namespace of the module-set block and of every additional module that is not private. */
private const val JETBRAINS_NAMESPACE: String = PluginModuleId.DEFAULT_NAMESPACE

/** The content of [spec], see [ProductContentPlan]. [owner] names the descriptor in a failure. */
internal fun productContentPlan(spec: ProductModulesContentSpec, owner: String): ProductContentPlan {
  val loadingOverrides = TreeMap<String, String>()
  for (entry in spec.moduleSets) {
    for ((module, loading) in entry.loadingOverrides) {
      check(loadingOverrides.put(module.value, loading.xmlValue) == null) {
        "$owner overrides the loading of '${module.value}' in two module sets"
      }
    }
  }
  val includes = LinkedHashMap<String, String>()
  for ((_, resourcePath, optional) in spec.deprecatedXmlIncludes) {
    // `resourcePathToXIncludePath` of the Product DSL renderer.
    val href = if (isModuleNameLikeFilename(resourcePath)) resourcePath else "/$resourcePath"
    check(includes.put(href, if (optional) "optional" else "required") == null) { "$owner includes '$href' twice" }
  }
  val contentModuleLoading = LinkedHashMap<String, String>()
  val contentModuleRequiredIfAvailable = LinkedHashMap<String, String>()
  val privateContentModules = ArrayList<String>()
  for (module in spec.additionalModules) {
    val name = module.moduleId.name
    when (module.moduleId.namespace) {
      null -> privateContentModules.add(name)
      JETBRAINS_NAMESPACE -> {}
      else -> error(
        "$owner states the additional module '$name' in the namespace '${module.moduleId.namespace}'." +
        " The descriptor writer composes only the '$JETBRAINS_NAMESPACE' namespace and a private module"
      )
    }
    nonDefaultLoading(module)?.let { contentModuleLoading.put(name, it) }
    module.requiredIfAvailable?.let { contentModuleRequiredIfAvailable.put(name, it.name) }
  }
  return ProductContentPlan(
    aliases = spec.productModuleAliases.map { it.value },
    includes = includes,
    moduleSets = spec.moduleSets.map { MODULE_SET_PREFIX + it.moduleSet.name },
    loadingOverrides = loadingOverrides,
    contentModules = spec.additionalModules.map { it.moduleId.name },
    privateContentModules = privateContentModules,
    contentModuleLoading = contentModuleLoading,
    contentModuleRequiredIfAvailable = contentModuleRequiredIfAvailable,
  )
}

/** The loading rule of [module] as the renderer writes it, or `null` for the default, which it omits. */
private fun nonDefaultLoading(module: ContentModule): String? {
  return if (module.loading == ModuleLoadingRuleValue.OPTIONAL) null else module.loading.xmlValue
}

/**
 * The rows of the module-set table for every set that [spec] reaches, keyed by the set name. Only the Product DSL facts:
 * the members and the nested sets in DSL order, the loading facts and the alias. [owner] names the descriptor in a failure.
 */
internal fun moduleSetRows(spec: ProductModulesContentSpec, owner: String): Map<String, ModuleSetData> {
  val result = TreeMap<String, ModuleSetData>()
  fun visit(moduleSet: ModuleSet) {
    val row = moduleSetRow(moduleSet)
    val earlier = result.putIfAbsent(row.name, row)
    if (earlier != null) {
      check(earlier == row) { "$owner reaches two module sets named '${row.name}': $earlier != $row" }
      return
    }
    for (nested in moduleSet.nestedSets) {
      visit(nested)
    }
  }
  spec.moduleSets.forEach { visit(it.moduleSet) }
  return result
}

private fun moduleSetRow(moduleSet: ModuleSet): ModuleSetData {
  val loading = LinkedHashMap<String, String>()
  val requiredIfAvailable = LinkedHashMap<String, String>()
  for (module in moduleSet.modules) {
    val name = module.moduleId.name
    check('/' !in name) {
      "Module set '${moduleSet.name}' states '$name'. The table keys a member by its module, so a member name holds no '/'"
    }
    nonDefaultLoading(module)?.let { loading.put(name, it) }
    module.requiredIfAvailable?.let { requiredIfAvailable.put(name, it.name) }
  }
  return ModuleSetData(
    name = MODULE_SET_PREFIX + moduleSet.name,
    modules = moduleSet.modules.map { it.moduleId.name },
    nested = moduleSet.nestedSets.map { MODULE_SET_PREFIX + it.name },
    loading = loading,
    requiredIfAvailable = requiredIfAvailable,
    alias = moduleSet.alias?.value,
  )
}

/** Adds [rows] to [table]. A set that both state must have equal Product DSL facts. [owner] names [rows] in a failure. */
internal fun mergeModuleSetRows(table: MutableMap<String, ModuleSetData>, rows: Map<String, ModuleSetData>, owner: String) {
  for ((name, row) in rows) {
    val earlier = table.putIfAbsent(name, row) ?: continue
    check(earlier == row) { "$owner states the module set '$name' differently from an earlier product: $earlier != $row" }
  }
}

/** One `<module/>` row of a composed `<content>` block. */
internal data class ProductContentRow(
  @JvmField val name: String,
  @JvmField val loading: String?,
  @JvmField val requiredIfAvailable: String?,
)

/** One `<content>` block: its namespace, or `null` for a block without one, and its rows in order. */
internal data class ProductContentBlock(
  @JvmField val namespace: String?,
  @JvmField val rows: List<ProductContentRow>,
)

/** The element structure of a composed product descriptor, before the descriptor writer resolves its includes. */
internal data class ComposedProductContent(
  /** The `<module value>` aliases in element order. */
  @JvmField val aliases: List<String>,
  /** The includes as `<kind>=<href>`, in element order. */
  @JvmField val includes: List<String>,
  @JvmField val blocks: List<ProductContentBlock>,
)

private class MutableRow(@JvmField val name: String, @JvmField var loading: String?, @JvmField val requiredIfAvailable: String?)

private fun normalizedLoading(rule: String?): String? = if (rule.isNullOrEmpty() || rule == ModuleLoadingRuleValue.OPTIONAL.xmlValue) null else rule

/**
 * The element structure that the macro and the descriptor writer compose from [content] and [table].
 *
 * The walk is `product_content_rows` of `dev_dist_product_content.bzl`, step by step: pre-order over
 * [ProductContentPlan.moduleSets], a set once, the own members of a set before its nested sets, no row for a set without
 * an own member. An override applies to the own members of a top-level set. A set that a top-level entry reaches again
 * with overrides takes them on its earlier rows, but only when none of them carries a non-default loading.
 *
 * The writer then writes the sorted aliases, the includes, one `jetbrains` block of the set rows when it has a row, and
 * the additional modules grouped by namespace in first-seen order.
 */
internal fun composeProductContent(content: ProductContentPlan, table: Map<String, ModuleSetData>): ComposedProductContent {
  val aliases = HashSet(content.aliases)
  val rows = ArrayList<MutableRow>()
  val rowsOfSet = HashMap<String, List<MutableRow>>()
  for (topLevel in content.moduleSets) {
    val stack = ArrayList<Pair<String, Boolean>>()
    stack.add(topLevel to true)
    while (stack.isNotEmpty()) {
      val (setName, isTopLevel) = stack.removeAt(stack.lastIndex)
      val moduleSet = requireNotNull(table.get(setName)) { "The module-set table has no set '$setName'" }
      val overrides = if (isTopLevel) moduleSet.modules.mapNotNull { name -> content.loadingOverrides.get(name)?.let { name to it } }.toMap() else emptyMap()
      val existing = rowsOfSet.get(setName)
      if (existing != null) {
        if (overrides.isNotEmpty() && existing.isNotEmpty() && existing.none { it.loading != null }) {
          for (row in existing) {
            overrides.get(row.name)?.let { row.loading = normalizedLoading(it) }
          }
        }
        continue
      }
      val ownRows = moduleSet.modules.map { name ->
        MutableRow(name = name, loading = normalizedLoading(overrides.get(name) ?: moduleSet.loading.get(name)), requiredIfAvailable = moduleSet.requiredIfAvailable.get(name))
      }
      rowsOfSet.put(setName, ownRows)
      moduleSet.alias?.let { aliases.add(it) }
      rows.addAll(ownRows)
      for (nested in moduleSet.nested.asReversed()) {
        stack.add(nested to false)
      }
    }
  }

  val blocks = ArrayList<ProductContentBlock>()
  if (rows.isNotEmpty()) {
    blocks.add(ProductContentBlock(JETBRAINS_NAMESPACE, rows.map { ProductContentRow(it.name, it.loading, it.requiredIfAvailable) }))
  }
  val private = content.privateContentModules.toHashSet()
  val additional = LinkedHashMap<String?, MutableList<ProductContentRow>>()
  for (name in content.contentModules) {
    val namespace = if (name in private) null else JETBRAINS_NAMESPACE
    additional.computeIfAbsent(namespace) { ArrayList() }.add(ProductContentRow(
      name = name,
      loading = normalizedLoading(content.contentModuleLoading.get(name)),
      requiredIfAvailable = content.contentModuleRequiredIfAvailable.get(name),
    ))
  }
  additional.mapTo(blocks) { (namespace, blockRows) -> ProductContentBlock(namespace, blockRows) }
  return ComposedProductContent(
    aliases = aliases.sorted(),
    includes = content.includes.map { (href, kind) -> "$kind=$href" },
    blocks = blocks,
  )
}

/**
 * The element structure of a rendered product content [xml], in the terms of [ComposedProductContent]. A required include
 * must stay an `xi:include` in [xml], because the descriptor writer resolves it. A root child that the writer does not
 * compose fails.
 */
internal fun parseProductContent(xml: String): ComposedProductContent {
  val root = JDOMUtil.load(xml)
  val aliases = ArrayList<String>()
  val includes = ArrayList<String>()
  val blocks = ArrayList<ProductContentBlock>()
  for (child in root.children) {
    when (child.name) {
      "id" -> {}
      "module" -> aliases.add(requireNotNull(child.getAttributeValue("value")) { "A root <module/> states no value" })
      "include" -> {
        check(child.namespace == JDOMUtil.XINCLUDE_NAMESPACE) { "The product content has an <include> outside the XInclude namespace" }
        val href = requireNotNull(child.getAttributeValue("href")) { "An xi:include states no href" }
        val kind = if (child.getChild("fallback", child.namespace) != null) "optional" else "required"
        includes.add("$kind=$href")
      }
      "content" -> blocks.add(ProductContentBlock(
        namespace = child.getAttributeValue("namespace"),
        rows = child.getChildren("module").map { module ->
          ProductContentRow(
            name = requireNotNull(module.getAttributeValue("name")) { "A <module/> states no name" },
            loading = normalizedLoading(module.getAttributeValue("loading")),
            requiredIfAvailable = module.getAttributeValue("required-if-available"),
          )
        },
      ))
      else -> error("The product content has the root child <${child.qualifiedName}>, which the descriptor writer does not compose")
    }
  }
  return ComposedProductContent(aliases = aliases, includes = includes, blocks = blocks)
}

/**
 * Fails when the composition of [content] over [table] differs from the Kotlin rendering [xml], see ADR 0037. The
 * message names [owner] and the first row that differs.
 */
internal fun checkProductContent(owner: String, content: ProductContentPlan, table: Map<String, ModuleSetData>, xml: String) {
  val composed = composeProductContent(content, table)
  val rendered = parseProductContent(xml)
  firstDifference("alias", composed.aliases, rendered.aliases)?.let { error("$owner: the composition differs from the Product DSL rendering: $it") }
  firstDifference("include", composed.includes, rendered.includes)?.let { error("$owner: the composition differs from the Product DSL rendering: $it") }
  val composedRows = composed.blocks.flatMapIndexed { index, block -> block.rows.map { "block $index (namespace ${block.namespace}): $it" } }
  val renderedRows = rendered.blocks.flatMapIndexed { index, block -> block.rows.map { "block $index (namespace ${block.namespace}): $it" } }
  firstDifference("content row", composedRows, renderedRows)?.let { error("$owner: the composition differs from the Product DSL rendering: $it") }
  check(composed.blocks.size == rendered.blocks.size) {
    "$owner: the composition has ${composed.blocks.size} content blocks, and the Product DSL rendering has ${rendered.blocks.size}"
  }
}

/** The first position where [composed] and [rendered] differ, described for a failure, or `null` when they are equal. */
private fun firstDifference(kind: String, composed: List<String>, rendered: List<String>): String? {
  for (index in 0 until maxOf(composed.size, rendered.size)) {
    val left = composed.getOrNull(index)
    val right = rendered.getOrNull(index)
    if (left != right) {
      return "$kind $index is '${left ?: "nothing"}' in the composition and '${right ?: "nothing"}' in the rendering"
    }
  }
  return null
}

private inline fun <T : Collection<*>> T.ifNotEmpty(action: (T) -> Unit) {
  if (isNotEmpty()) action(this)
}

private inline fun <T : Map<*, *>> T.ifNotEmpty(action: (T) -> Unit) {
  if (isNotEmpty()) action(this)
}

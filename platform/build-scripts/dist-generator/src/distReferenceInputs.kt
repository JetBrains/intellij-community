// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.distGenerator

import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import java.util.TreeMap
import java.util.TreeSet

/** The labels that the reference fragments declare, relative to the root of the half. Only the reference macros load it. */
internal const val DIST_REFERENCE_INPUTS_RELATIVE_PATH: String = "build/dist_reference_inputs.bzl"

/** The runtime module repository payload of a product as `dist_fragment_inputs.bzl` states it: JPS names, not labels. */
internal class DistReferencePayload(
  @JvmField val modules: List<String>,
  @JvmField val projectLibraries: List<String>,
  @JvmField val moduleSets: List<String>,
)

/**
 * The names of one split product that the generator resolves or checks for the references.
 *
 * [runtimeClasspathModules] are the runtime classpath seeds of the `platform_lib` payload. [runtimeModuleRepository] is
 * the runtime module repository payload, or `null` when the product has no such payload.
 */
internal class DistReferenceProduct(
  @JvmField val platformPrefix: String,
  @JvmField val buildModules: List<String>,
  @JvmField val runtimeClasspathModules: List<String>,
  @JvmField val runtimeModuleRepository: DistReferencePayload?,
)

/**
 * The labels that the reference fragments of one product take from the generator. Each list is sorted and distinct.
 *
 * [buildModules] are the production outputs of the build modules. [runtimeClasspath] is the runtime classpath of a
 * platform patch. [runtimeModuleRepository] are the production outputs of the modules of the runtime module repository
 * payload.
 */
internal data class DistReferenceInputs(
  @JvmField val buildModules: List<String> = emptyList(),
  @JvmField val runtimeClasspath: List<String> = emptyList(),
  @JvmField val runtimeModuleRepository: List<String> = emptyList(),
)

/**
 * What the targets JSON and the JPS model state about the names of a product.
 *
 * [moduleDependencies] gives the modules that a module depends on outside the test scope. [projectLibraryReferences]
 * gives the project libraries that a module references in any scope. Both answer with an empty list for an unknown module.
 */
internal class DistReferenceInputModel(
  private val targets: BazelTargetsInfo.TargetsFile,
  @JvmField val moduleDependencies: (String) -> List<String>,
  private val projectLibraryReferences: (String) -> List<String>,
) {
  /** The production outputs of [module], or `null` when the converter records none. */
  fun moduleOutputs(module: String): List<String>? = targets.modules.get(module)?.productionTargets?.takeIf { it.isNotEmpty() }

  /** The jar targets of the module libraries of [module] and of the project libraries that it references. */
  fun moduleLibraryTargets(module: String): List<String> {
    val result = ArrayList<String>()
    targets.modules.get(module)?.moduleLibraries?.values?.flatMapTo(result) { it.jarTargets }
    for (library in projectLibraryReferences(module)) {
      targets.projectLibraries.get(library)?.jarTargets?.let(result::addAll)
    }
    return result
  }
}

/**
 * Resolves the build modules, the runtime classpath and the runtime module repository payload of [products] to labels,
 * keyed by product.
 *
 * The runtime classpath is the closure of the non-test module dependencies of the seeds. It is a superset of the JPS
 * runtime classpath, which leaves a provided-scope dependency out.
 *
 * A build module and a module of the runtime module repository payload give only the production outputs of the module.
 * So the run fails on a payload that names a module set, a project library, or a module with libraries.
 *
 * The run also fails on a name that the targets JSON does not have. The converter runs before the generator, so such a
 * name is a stale targets JSON or a generator defect. Each message names every such name.
 */
internal fun resolveDistReferenceInputs(
  products: List<DistReferenceProduct>,
  model: DistReferenceInputModel,
): Map<String, DistReferenceInputs> {
  val unknown = TreeSet<String>()
  val refused = TreeSet<String>()

  fun addModule(targets: MutableSet<String>, module: String, context: String) {
    val outputs = model.moduleOutputs(module)
    if (outputs == null) {
      unknown.add("$context: unknown module '$module'")
      return
    }
    targets.addAll(outputs)
    targets.addAll(model.moduleLibraryTargets(module))
  }

  fun runtimeClasspath(product: DistReferenceProduct): Set<String> {
    val reached = TreeSet<String>()
    val pending = ArrayDeque(product.runtimeClasspathModules)
    while (pending.isNotEmpty()) {
      val module = pending.removeFirst()
      if (reached.add(module)) {
        pending.addAll(model.moduleDependencies(module))
      }
    }
    val targets = TreeSet<String>()
    for (module in reached) {
      addModule(targets, module, context = "product '${product.platformPrefix}', payload '$PLATFORM_LIB_PAYLOAD', runtime classpath")
    }
    return targets
  }

  fun runtimeModuleRepository(prefix: String, payload: DistReferencePayload): Set<String> {
    val context = "product '$prefix', payload '$RUNTIME_MODULE_REPOSITORY_PAYLOAD'"
    payload.moduleSets.mapTo(refused) { "$context: module set '$it'" }
    payload.projectLibraries.mapTo(refused) { "$context: project library '$it'" }
    val targets = TreeSet<String>()
    for (module in payload.modules) {
      val outputs = model.moduleOutputs(module)
      if (outputs == null) {
        unknown.add("$context: unknown module '$module'")
      }
      else if (model.moduleLibraryTargets(module).isNotEmpty()) {
        refused.add("$context: module '$module' has libraries")
      }
      else {
        targets.addAll(outputs)
      }
    }
    return targets
  }

  fun buildModules(prefix: String, modules: List<String>): Set<String> {
    val targets = TreeSet<String>()
    for (module in modules) {
      val outputs = model.moduleOutputs(module)
      if (outputs == null) {
        unknown.add("product '$prefix', build modules: unknown module '$module'")
      }
      else {
        targets.addAll(outputs)
      }
    }
    return targets
  }

  val result = TreeMap<String, DistReferenceInputs>()
  for (product in products) {
    val prefix = product.platformPrefix
    result.put(prefix, DistReferenceInputs(
      buildModules = buildModules(prefix, product.buildModules).toList(),
      runtimeClasspath = runtimeClasspath(product).toList(),
      runtimeModuleRepository = product.runtimeModuleRepository?.let { runtimeModuleRepository(prefix, it) }.orEmpty().toList(),
    ))
  }
  check(unknown.isEmpty()) {
    "The reference inputs name ${unknown.size} name(s) that the targets JSON does not have. " +
    "Run the JPS-to-Bazel converter, then the generator again:\n  " + unknown.joinToString("\n  ")
  }
  check(refused.isEmpty()) {
    "The generator gives a module of the runtime module repository payload only its production labels. " +
    "Move these ${refused.size} name(s) out of the runtime module repository payload:\n  " + refused.joinToString("\n  ")
  }
  return result
}

private const val PLATFORM_LIB_PAYLOAD: String = "platform_lib"

private const val RUNTIME_MODULE_REPOSITORY_PAYLOAD: String = "platform_runtime_module_repository"

/**
 * `build/dist_reference_inputs.bzl` of [inputs], keyed by product in key order. A product with no label has no row, and
 * a row omits an empty field.
 *
 * The labels that every row states in a field are one private constant of that field, when two or more rows state the
 * field. The field of a row then states the constant and adds its own labels.
 */
internal fun renderDistReferenceInputs(inputs: Map<String, DistReferenceInputs>, generatedByHeader: String): String = buildString {
  append(generatedByHeader)
  append("#\n")
  append("# The labels that the reference fragments of the `jars`, `replay` and `runtime-repo` gates declare as their inputs.\n")
  append("# No distribution reads them. The generator resolves the names of `dist_reference_plan.bzl` and of\n")
  append("# `dist_fragment_inputs.bzl` through the targets JSON. Only the reference macros load this file, so a change here\n")
  append("# re-keys only the reference fragments.\n")
  append("#\n")
  append("# `build_modules` holds the production outputs of the modules whose product properties the reference assembler\n")
  append("# evaluates. `runtime_classpath` holds the outputs and the libraries of the non-test dependency closure of the\n")
  append("# `runtime_classpath_modules` seeds. `runtime_module_repository` holds the production outputs of the modules of the\n")
  append("# runtime module repository payload. A row omits an empty field, and a product without a label has no row.\n")
  append("#\n")
  append("# The labels that every row states in a field are one private constant of that field, when two or more rows state\n")
  append("# the field. The field of a row states the constant and adds its own labels. The reference macros take the union of\n")
  append("# the lists, so the order of the labels has no meaning.\n")
  val rows = inputs.filterValues { row -> REFERENCE_INPUT_FIELDS.any { it.labels(row).isNotEmpty() } }
  val shared = REFERENCE_INPUT_FIELDS.associateWith { field ->
    val lists = rows.values.map(field.labels).filter { it.isNotEmpty() }
    if (lists.size < 2) {
      emptySet()
    }
    else {
      lists.drop(1).fold(TreeSet(lists.first())) { result, list -> result.apply { retainAll(list.toSet()) } }
    }
  }
  for (field in REFERENCE_INPUT_FIELDS) {
    val labels = shared.getValue(field)
    if (labels.isEmpty()) {
      continue
    }
    append(field.constant).append(" = [\n")
    for (label in labels) {
      append(INDENT).append(quoteStarlarkString(label)).append(",\n")
    }
    append("]\n\n")
  }
  if (rows.isEmpty()) {
    append("DIST_REFERENCE_INPUTS = {}\n")
    return@buildString
  }
  append("DIST_REFERENCE_INPUTS = {\n")
  for ((product, row) in rows) {
    append(INDENT).append(quoteStarlarkString(product)).append(": struct(\n")
    for (field in REFERENCE_INPUT_FIELDS) {
      val labels = field.labels(row)
      if (labels.isEmpty()) {
        continue
      }
      val constant = shared.getValue(field)
      append(INDENT).append(INDENT).append(field.name).append(" = ")
      val own = labels.filterNot { it in constant }
      if (constant.isNotEmpty()) {
        append(field.constant)
        if (own.isNotEmpty()) {
          append(" + ")
        }
      }
      if (own.isNotEmpty()) {
        append("[\n")
        for (label in own) {
          append(INDENT).append(INDENT).append(INDENT).append(quoteStarlarkString(label)).append(",\n")
        }
        append(INDENT).append(INDENT).append("]")
      }
      append(",\n")
    }
    append(INDENT).append("),\n")
  }
  append("}\n")
}

/** A label list of a row of `build/dist_reference_inputs.bzl`: its field, the name of its shared constant, and its labels. */
private class ReferenceInputField(
  @JvmField val name: String,
  @JvmField val labels: (DistReferenceInputs) -> List<String>,
) {
  @JvmField val constant: String = "_" + name.uppercase()
}

private val REFERENCE_INPUT_FIELDS: List<ReferenceInputField> = listOf(
  ReferenceInputField("build_modules") { it.buildModules },
  ReferenceInputField("runtime_classpath") { it.runtimeClasspath },
  ReferenceInputField("runtime_module_repository") { it.runtimeModuleRepository },
)

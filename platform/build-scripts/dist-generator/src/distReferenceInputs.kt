// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.distGenerator

import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import java.util.TreeMap
import java.util.TreeSet

/** The runtime classpath that the reference fragments declare, relative to the root of the half. Only the reference macros load it. */
internal const val DIST_REFERENCE_INPUTS_RELATIVE_PATH: String = "build/dev_dist_reference_inputs.bzl"

/** The runtime module repository payload of a product as `dev_dist_fragment_inputs.bzl` states it: JPS names, not labels. */
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

/** The labels that the reference fragments of one product take from the generator. The list is sorted and distinct. */
internal data class DistReferenceInputs(
  @JvmField val runtimeClasspath: List<String>,
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
 * Resolves the runtime classpath of [products] to labels, keyed by product, and checks the names that the reference
 * macro resolves.
 *
 * The runtime classpath is the closure of the non-test module dependencies of the seeds. It is a superset of the JPS
 * runtime classpath, which leaves a provided-scope dependency out.
 *
 * The reference macro resolves a build module and a module of the runtime module repository payload through the module
 * target index at load time. That index gives the production labels of a module only. So the run fails on a payload
 * that names a module set, a project library, or a module with libraries.
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

  fun checkRuntimeModuleRepository(prefix: String, payload: DistReferencePayload) {
    val context = "product '$prefix', payload '$RUNTIME_MODULE_REPOSITORY_PAYLOAD'"
    payload.moduleSets.mapTo(refused) { "$context: module set '$it'" }
    payload.projectLibraries.mapTo(refused) { "$context: project library '$it'" }
    for (module in payload.modules) {
      if (model.moduleOutputs(module) == null) {
        unknown.add("$context: unknown module '$module'")
      }
      else if (model.moduleLibraryTargets(module).isNotEmpty()) {
        refused.add("$context: module '$module' has libraries")
      }
    }
  }

  val result = TreeMap<String, DistReferenceInputs>()
  for (product in products) {
    val prefix = product.platformPrefix
    for (module in product.buildModules) {
      if (model.moduleOutputs(module) == null) {
        unknown.add("product '$prefix', build modules: unknown module '$module'")
      }
    }
    product.runtimeModuleRepository?.let { checkRuntimeModuleRepository(prefix, it) }
    result.put(prefix, DistReferenceInputs(runtimeClasspath = runtimeClasspath(product).toList()))
  }
  check(unknown.isEmpty()) {
    "The reference inputs name ${unknown.size} name(s) that the targets JSON does not have. " +
    "Run the JPS-to-Bazel converter, then the generator again:\n  " + unknown.joinToString("\n  ")
  }
  check(refused.isEmpty()) {
    "The reference macro resolves a module name only, and gets the production labels of the module. " +
    "Move these ${refused.size} name(s) out of the runtime module repository payload:\n  " + refused.joinToString("\n  ")
  }
  return result
}

private const val PLATFORM_LIB_PAYLOAD: String = "platform_lib"

private const val RUNTIME_MODULE_REPOSITORY_PAYLOAD: String = "platform_runtime_module_repository"

/**
 * `build/dev_dist_reference_inputs.bzl` of [inputs], keyed by product in key order. Only a product with a runtime
 * classpath has a row.
 *
 * The labels that every row states are one private constant `_RUNTIME_CLASSPATH`, when two or more rows exist. A row
 * then states the constant and adds its own labels.
 */
internal fun renderDistReferenceInputs(inputs: Map<String, DistReferenceInputs>, generatedByHeader: String): String = buildString {
  append(generatedByHeader)
  append("#\n")
  append("# The runtime classpath that a platform patch of a product loads, as labels. Only the reference fragments of the\n")
  append("# `jars`, `replay` and `runtime-repo` gates read it, and no distribution reads it. The generator resolves the\n")
  append("# `runtime_classpath_modules` seeds of `dev_dist_fragment_inputs.bzl` through the targets JSON. Only the reference\n")
  append("# macros load this file, so a change here does not run the JPS bridge again.\n")
  append("#\n")
  append("# `runtime_classpath` holds the outputs and the libraries of the non-test dependency closure of the seeds. A\n")
  append("# product without a row has an empty runtime classpath. The reference macro takes the platform from the platform\n")
  append("# payload, and it resolves the build modules and the repository root through the module target index.\n")
  append("#\n")
  append("# The labels that every row states are one private constant `_RUNTIME_CLASSPATH`, when two or more rows exist.\n")
  append("# A row states the constant and adds its own labels. The reference macros take the union of the lists, so the\n")
  append("# order of the labels has no meaning.\n")
  val rows = inputs.filterValues { it.runtimeClasspath.isNotEmpty() }
  val shared = if (rows.size < 2) {
    emptySet()
  }
  else {
    rows.values.drop(1).fold(TreeSet(rows.values.first().runtimeClasspath)) { result, row -> result.apply { retainAll(row.runtimeClasspath.toSet()) } }
  }
  if (shared.isNotEmpty()) {
    append("_RUNTIME_CLASSPATH = [\n")
    for (label in shared) {
      append(INDENT).append(quoteStarlarkString(label)).append(",\n")
    }
    append("]\n\n")
  }
  if (rows.isEmpty()) {
    append("DEV_DIST_REFERENCE_INPUTS = {}\n")
    return@buildString
  }
  append("DEV_DIST_REFERENCE_INPUTS = {\n")
  for ((product, row) in rows) {
    append(INDENT).append(quoteStarlarkString(product)).append(": struct(\n")
    append(INDENT).append(INDENT).append("runtime_classpath = ")
    val own = row.runtimeClasspath.filterNot { it in shared }
    if (shared.isNotEmpty()) {
      append("_RUNTIME_CLASSPATH")
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
    append(INDENT).append("),\n")
  }
  append("}\n")
}

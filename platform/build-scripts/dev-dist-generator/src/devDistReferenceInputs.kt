// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import java.util.TreeMap
import java.util.TreeSet

/** The labels that the reference fragments declare, relative to the root of the half. Only the reference macros load it. */
internal const val DEV_DIST_REFERENCE_INPUTS_RELATIVE_PATH: String = "build/dev_dist_reference_inputs.bzl"

/** One payload of a product as `dev_dist_fragment_inputs.bzl` states it: JPS names, not labels. */
internal class DevDistReferencePayload(
  @JvmField val modules: List<String>,
  @JvmField val projectLibraries: List<String>,
  @JvmField val moduleSets: List<String>,
  @JvmField val runtimeClasspathModules: List<String>,
)

/**
 * The facts of one split product that the reference inputs resolve.
 *
 * [platformLib] and [runtimeModuleRepository] are the payloads of those names, or `null` when the product has no such
 * payload. [embeddedFrontend] is the split product whose platform the runtime module repository reference also lays out.
 */
internal class DevDistReferenceProduct(
  @JvmField val platformPrefix: String,
  @JvmField val buildModules: List<String>,
  @JvmField val embeddedFrontend: String?,
  @JvmField val platformLib: DevDistReferencePayload?,
  @JvmField val runtimeModuleRepository: DevDistReferencePayload?,
)

/**
 * The labels that the reference fragments of one product declare. Every list is sorted and distinct.
 *
 * [platformLib] is the whole `platform_lib` declaration, or `null` when no runtime module repository reference lays the
 * platform of the product out. [runtimeModuleRepository] holds the inputs of the own payload of that reference, or `null`
 * for a product without it.
 */
internal data class DevDistReferenceInputs(
  @JvmField val buildModules: List<String>,
  @JvmField val runtimeClasspath: List<String>,
  @JvmField val platformLib: List<String>?,
  @JvmField val runtimeModuleRepository: List<String>?,
)

/**
 * What the targets JSON and the JPS model state about the names of a payload.
 *
 * [moduleDependencies] gives the modules that a module depends on outside the test scope. [projectLibraryReferences]
 * gives the project libraries that a module references in any scope. Both answer with an empty list for an unknown module.
 */
internal class DevDistReferenceInputModel(
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

  /** The jar targets of the project library [name], or `null` when the converter records no such library. */
  fun projectLibraryTargets(name: String): List<String>? = targets.projectLibraries.get(name)?.jarTargets
}

/**
 * Resolves the payloads and the build modules of [products] to labels, keyed by product.
 *
 * A payload takes the members of every module set it references from [moduleSets], with the nested sets. The runtime
 * classpath is the closure of the non-test module dependencies of the seeds. It is a superset of the JPS runtime
 * classpath, which leaves a provided-scope dependency out.
 *
 * The run fails on a name that the targets JSON or [moduleSets] does not have. The converter runs before the generator,
 * so such a name is a stale targets JSON or a generator defect. The message names every such name.
 */
internal fun resolveDevDistReferenceInputs(
  products: List<DevDistReferenceProduct>,
  moduleSets: Map<String, ModuleSetData>,
  model: DevDistReferenceInputModel,
): Map<String, DevDistReferenceInputs> {
  val unknown = TreeSet<String>()
  val byName = products.associateBy { it.platformPrefix }

  fun addModule(targets: MutableSet<String>, module: String, includeLibraries: Boolean, context: String) {
    val outputs = model.moduleOutputs(module)
    if (outputs == null) {
      unknown.add("$context: unknown module '$module'")
      return
    }
    targets.addAll(outputs)
    if (includeLibraries) {
      targets.addAll(model.moduleLibraryTargets(module))
    }
  }

  fun addProjectLibraries(targets: MutableSet<String>, payload: DevDistReferencePayload, context: String) {
    for (library in payload.projectLibraries) {
      val libraryTargets = model.projectLibraryTargets(library)
      if (libraryTargets == null) {
        unknown.add("$context: unknown project library '$library'")
      }
      else {
        targets.addAll(libraryTargets)
      }
    }
  }

  fun expandedModules(payload: DevDistReferencePayload, context: String): Set<String> {
    val result = TreeSet(payload.modules)
    val pending = ArrayDeque(payload.moduleSets)
    val visited = HashSet<String>()
    while (pending.isNotEmpty()) {
      val setName = pending.removeFirst()
      if (!visited.add(setName)) {
        continue
      }
      val moduleSet = moduleSets.get(setName)
      if (moduleSet == null) {
        unknown.add("$context: unknown module set '$setName'")
        continue
      }
      result.addAll(moduleSet.modules)
      pending.addAll(moduleSet.nested)
    }
    return result
  }

  fun runtimeClasspath(payload: DevDistReferencePayload, context: String): Set<String> {
    val reached = TreeSet<String>()
    val pending = ArrayDeque(payload.runtimeClasspathModules)
    while (pending.isNotEmpty()) {
      val module = pending.removeFirst()
      if (reached.add(module)) {
        pending.addAll(model.moduleDependencies(module))
      }
    }
    val targets = TreeSet<String>()
    for (module in reached) {
      addModule(targets, module, includeLibraries = true, context = "$context, runtime classpath")
    }
    return targets
  }

  // The products whose platform a runtime module repository reference lays out: the product itself and its embedded frontend.
  val platformReaders = TreeSet<String>()
  for (product in products) {
    if (product.runtimeModuleRepository == null) {
      continue
    }
    platformReaders.add(product.platformPrefix)
    val frontend = product.embeddedFrontend ?: continue
    check(byName.get(frontend)?.platformLib != null) {
      "The embedded frontend '$frontend' of '${product.platformPrefix}' has no `$PLATFORM_LIB_PAYLOAD` payload"
    }
    platformReaders.add(frontend)
  }

  val result = TreeMap<String, DevDistReferenceInputs>()
  for (product in products) {
    val prefix = product.platformPrefix
    val buildModules = TreeSet<String>()
    for (module in product.buildModules) {
      addModule(buildModules, module, includeLibraries = false, context = "product '$prefix', build modules")
    }

    val platformLibContext = "product '$prefix', payload '$PLATFORM_LIB_PAYLOAD'"
    val runtimeClasspath = product.platformLib?.let { runtimeClasspath(it, platformLibContext) }.orEmpty()
    val platformLib = product.platformLib?.takeIf { prefix in platformReaders }?.let { payload ->
      val targets = TreeSet<String>()
      for (module in expandedModules(payload, platformLibContext)) {
        addModule(targets, module, includeLibraries = true, context = platformLibContext)
      }
      addProjectLibraries(targets, payload, platformLibContext)
      targets.addAll(runtimeClasspath)
      targets.toList()
    }
    check(platformLib != null || prefix !in platformReaders) {
      "The runtime module repository reference of '$prefix' lays out a platform without a `$PLATFORM_LIB_PAYLOAD` payload"
    }

    val runtimeModuleRepository = product.runtimeModuleRepository?.let { payload ->
      val context = "product '$prefix', payload '$RUNTIME_MODULE_REPOSITORY_PAYLOAD'"
      val targets = TreeSet<String>()
      for (module in expandedModules(payload, context)) {
        addModule(targets, module, includeLibraries = true, context = context)
      }
      addProjectLibraries(targets, payload, context)
      targets.toList()
    }

    result.put(prefix, DevDistReferenceInputs(
      buildModules = buildModules.toList(),
      runtimeClasspath = runtimeClasspath.toList(),
      platformLib = platformLib,
      runtimeModuleRepository = runtimeModuleRepository,
    ))
  }
  check(unknown.isEmpty()) {
    "The reference inputs name ${unknown.size} name(s) that the targets JSON does not have. " +
    "Run the JPS-to-Bazel converter, then the generator again:\n  " + unknown.joinToString("\n  ")
  }
  return result
}

private const val PLATFORM_LIB_PAYLOAD: String = "platform_lib"

private const val RUNTIME_MODULE_REPOSITORY_PAYLOAD: String = "platform_runtime_module_repository"

/** The fields of a [DevDistReferenceInputs] struct in file order, with the value of each. An empty optional list is left out. */
private fun referenceInputFields(inputs: DevDistReferenceInputs): List<Pair<String, List<String>>> {
  val result = ArrayList<Pair<String, List<String>>>()
  if (inputs.buildModules.isNotEmpty()) {
    result.add("build_modules" to inputs.buildModules)
  }
  if (inputs.runtimeClasspath.isNotEmpty()) {
    result.add("runtime_classpath" to inputs.runtimeClasspath)
  }
  inputs.platformLib?.let { result.add("platform_lib" to it) }
  inputs.runtimeModuleRepository?.let { result.add("runtime_module_repository" to it) }
  return result
}

private val REFERENCE_INPUT_FIELDS: List<String> = listOf("build_modules", "runtime_classpath", "platform_lib", "runtime_module_repository")

/**
 * `build/dev_dist_reference_inputs.bzl` of [inputs], keyed by product in key order.
 *
 * The labels that every product with a field states are one private constant `_<FIELD>`, when two or more products
 * state the field. A product then states the constant and adds its own labels.
 */
internal fun renderDevDistReferenceInputs(inputs: Map<String, DevDistReferenceInputs>, generatedByHeader: String): String = buildString {
  append(generatedByHeader)
  append("#\n")
  append("# The labels that the reference fragments of the `jars`, `replay` and `runtime-repo` gates declare as their inputs.\n")
  append("# No distribution reads them. The generator resolves the names of `dev_dist_fragment_inputs.bzl` and\n")
  append("# `dev_dist_reference_plan.bzl` through the targets JSON. Only the reference macros load this file, so a change\n")
  append("# here does not run the JPS bridge again.\n")
  append("#\n")
  append("# `build_modules` holds the outputs of the build modules. `runtime_classpath` holds the outputs and the libraries of\n")
  append("# the non-test dependency closure of the runtime classpath seeds. `platform_lib` is the whole `platform_lib`\n")
  append("# declaration: the inputs of every payload module, the own library entries and the runtime classpath. Only a\n")
  append("# product whose platform a runtime module repository reference lays out states it. `runtime_module_repository`\n")
  append("# holds the inputs of the own payload of that reference.\n")
  append("#\n")
  append("# The labels that every product with a field states are one private constant `_<FIELD>`, when two or more\n")
  append("# products state the field. A product states the constant and adds its own labels. The reference macros take\n")
  append("# the union of the lists, so the order of the labels has no meaning.\n")
  val fieldsByProduct = inputs.mapValues { referenceInputFields(it.value).toMap() }
  val common = LinkedHashMap<String, Set<String>>()
  for (field in REFERENCE_INPUT_FIELDS) {
    val values = fieldsByProduct.values.mapNotNull { it.get(field) }
    if (values.size < 2) {
      continue
    }
    val shared = values.drop(1).fold(TreeSet(values.first())) { result, value -> result.apply { retainAll(value.toSet()) } }
    if (shared.isEmpty()) {
      continue
    }
    common.put(field, shared)
    append("_").append(field.uppercase()).append(" = [\n")
    for (label in shared) {
      append(INDENT).append(quoteStarlarkString(label)).append(",\n")
    }
    append("]\n\n")
  }
  append("DEV_DIST_REFERENCE_INPUTS = {\n")
  for ((product, fields) in fieldsByProduct) {
    append(INDENT).append(quoteStarlarkString(product)).append(": struct(")
    if (fields.isNotEmpty()) {
      append("\n")
    }
    for ((field, labels) in fields) {
      append(INDENT).append(INDENT).append(field).append(" = ")
      val shared = common.get(field)
      val own = if (shared == null) labels else labels.filterNot { it in shared }
      if (shared != null) {
        append("_").append(field.uppercase())
        if (own.isEmpty()) {
          append(",\n")
          continue
        }
        append(" + ")
      }
      if (own.isEmpty()) {
        append("[],\n")
        continue
      }
      append("[\n")
      for (label in own) {
        append(INDENT).append(INDENT).append(INDENT).append(quoteStarlarkString(label)).append(",\n")
      }
      append(INDENT).append(INDENT).append("],\n")
    }
    if (fields.isNotEmpty()) {
      append(INDENT)
    }
    append("),\n")
  }
  append("}\n")
}

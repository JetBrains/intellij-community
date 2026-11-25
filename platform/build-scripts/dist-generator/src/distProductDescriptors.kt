// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.distGenerator

import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

/**
 * The files of [PRODUCT_DESCRIPTOR_PACKAGE], keyed by their project-relative paths: the `BUILD.bazel` of the package.
 *
 * A package of its own, so a product in `//build` is not analysed again when the content of one product changes. A plan
 * keeps its labels in the recorded form, and the `BUILD.bazel` spells them for a package of the half of [index], see
 * [DistBazelIndex.planLabel].
 */
internal fun renderProductDescriptorPackage(plans: List<ProductDescriptorPlan>, index: DistBazelIndex, half: DistHalf): Map<String, String> {
  return mapOf("$PRODUCT_DESCRIPTOR_PACKAGE/BUILD.bazel" to renderProductDescriptorBuildFile(plans, index, half))
}

private fun renderProductDescriptorBuildFile(plans: List<ProductDescriptorPlan>, index: DistBazelIndex, half: DistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# The generated entries of the application-info module jar of each product: the product descriptor, and the\n")
  append("# application info of a product with `appInfoXmlReplacements`. The application info action replaces only these\n")
  append("# markers, and the run time reads the build number from `build.txt`. `dist_platform_jar` patches the outputs\n")
  append("# into the jar, see the `patches` of the residual jar in `build/dist_fragment_inputs.bzl`. A product without\n")
  append("# replacements patches its application info source as it is.\n")
  append("#\n")
  append("# A target states the Product DSL content of its product: the aliases, the deprecated includes, the top-level module\n")
  append("# sets with their loading overrides, and the additional modules. The macro walks `DIST_MODULE_SETS` as\n")
  append("# `processAndGetProductPluginContentModules` inlines the sets, and the descriptor writer composes the content. The\n")
  append("# macro takes the descriptor row of each set member from the table. `module_descriptors` states the other rows whose\n")
  append("# load path is the base name of the file, and `descriptors` states the rest. The label of a conventional row is in\n")
  append("# the recorded form. Every target is `manual`, which the macros add.\n")
  if (plans.isEmpty()) {
    return@buildString
  }
  append("\n")
  // The load lines in the order buildifier sorts them: a file of an explicit repository before a file of this one.
  val loads = ArrayList<LoadStatement>()
  val productDescriptorSymbols = if (plans.any(ProductDescriptorPlan::hasApplicationInfo)) {
    listOf("dist_product_application_info", "dist_product_descriptor")
  }
  else {
    listOf("dist_product_descriptor")
  }
  loads.add(LoadStatement(index.planLabel(PRODUCT_DESCRIPTOR_RULE), productDescriptorSymbols))
  loads.add(LoadStatement(DIST_MODULE_SETS_BZL, listOf(DIST_MODULE_SETS_SYMBOL)))
  val loadOrder = BazelLabelComparator(forLoadStatements = true)
  for (load in loads.sortedWith { a, b -> loadOrder.compare(a.bzlFile, b.bzlFile) }) {
    append(load.render()).append("\n")
  }
  for (plan in plans.sortedBy(ProductDescriptorPlan::name)) {
    append("\n")
    // `name` first, then the attributes in buildifier's alphabetical order, so a regenerate leaves the file unchanged.
    val attributes = TreeMap(plan.content.attributes(DIST_MODULE_SETS_SYMBOL))
    val descriptors = productDescriptorRows(
      stated = plan.descriptors.entries.associate { index.planLabel(it.key) to it.value },
      content = plan.content,
      refused = plan.refusedContentModules,
      index = index,
    )
    putDescriptorRows(attributes, descriptors)
    if (plan.libraryDescriptors.isNotEmpty()) {
      attributes.put("library_descriptors", plan.libraryDescriptors.entries.associateTo(LinkedHashMap()) { index.planLabel(it.key) to it.value })
    }
    attributes.put("main_module", plan.mainModule)
    if (plan.refusedContentModules.isNotEmpty()) {
      attributes.put("refused_content_modules", plan.refusedContentModules)
    }
    if (plan.scrambledContentModules.isNotEmpty()) {
      attributes.put("scrambled_content_modules", plan.scrambledContentModules)
    }
    val descriptor = Target("dist_product_descriptor")
    descriptor.option("name", "${plan.name}_product_descriptor")
    for ((key, value) in attributes) {
      descriptor.option(key, value)
    }
    append(descriptor.render())

    if (plan.hasApplicationInfo) {
      append("\n")
      val applicationInfo = Target("dist_product_application_info")
      applicationInfo.option("name", "${plan.name}_application_info")
      applicationInfo.option("replacements", plan.replacements.unsorted())
      applicationInfo.option("source", index.planLabel(plan.applicationInfo))
      append(applicationInfo.render())
    }
  }
}

/** The `.bzl` file that exports `dist_product_descriptor` and `dist_product_application_info`. */
private const val PRODUCT_DESCRIPTOR_RULE: String = "@community//platform/build-scripts/bazel-rules:dist_product_descriptor.bzl"

/**
 * The `.xml` files of [PRODUCT_DESCRIPTOR_PACKAGE], as project-relative paths. The run writes none, because the macro
 * composes the content from the module-set table, so each one is stale.
 */
internal fun staleProductDescriptorSources(projectRoot: Path): List<String> {
  val directory = projectRoot.resolve(PRODUCT_DESCRIPTOR_PACKAGE)
  if (!Files.isDirectory(directory)) {
    return emptyList()
  }
  return Files.newDirectoryStream(directory, "*.xml").use { stream ->
    stream.map { "$PRODUCT_DESCRIPTOR_PACKAGE/${it.fileName}" }.sorted()
  }
}

/**
 * Puts the `descriptors` rows into the `module_descriptors` and `descriptors` attributes of a call, see
 * [splitDescriptorRows]. An empty attribute is left out.
 */
internal fun putDescriptorRows(attributes: MutableMap<String, Any>, rows: Map<String, String>) {
  val split = splitDescriptorRows(rows)
  if (split.descriptors.isNotEmpty()) {
    attributes.put("descriptors", LinkedHashMap(split.descriptors))
  }
  if (split.moduleDescriptors.isNotEmpty()) {
    attributes.put("module_descriptors", split.moduleDescriptors)
  }
}

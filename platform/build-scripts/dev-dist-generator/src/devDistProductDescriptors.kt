// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

/**
 * The files of [PRODUCT_DESCRIPTOR_PACKAGE], keyed by their project-relative paths: the `BUILD.bazel` of the package.
 *
 * A package of its own, so a product in `//build` is not analysed again when the content of one product changes. A plan
 * keeps its labels in the recorded form, and the `BUILD.bazel` spells them for a package of the half of [index], see
 * [DevDistBazelIndex.planLabel].
 */
internal fun renderProductDescriptorPackage(plans: List<ProductDescriptorPlan>, index: DevDistBazelIndex, half: DevDistHalf): Map<String, String> {
  return mapOf("$PRODUCT_DESCRIPTOR_PACKAGE/BUILD.bazel" to renderProductDescriptorBuildFile(plans, index, half))
}

private fun renderProductDescriptorBuildFile(plans: List<ProductDescriptorPlan>, index: DevDistBazelIndex, half: DevDistHalf): String = buildString {
  append(half.generatedByHeader)
  append("#\n")
  append("# The generated entries of the application-info module jar of each product: the product descriptor, and the\n")
  append("# application info of a product with `appInfoXmlReplacements`. The application info action replaces only these\n")
  append("# markers, and the run time reads the build number from `build.txt`. `dev_dist_platform_jar` patches the outputs\n")
  append("# into the jar, see the `patches` of the residual jar in `build/dev_dist_fragment_inputs.bzl`. A product without\n")
  append("# replacements patches its application info source as it is.\n")
  append("#\n")
  append("# A target states the Product DSL content of its product: the aliases, the deprecated includes, the top-level module\n")
  append("# sets with their loading overrides, and the additional modules. The macro walks `DEV_DIST_MODULE_SETS` as\n")
  append("# `processAndGetProductPluginContentModules` inlines the sets, and the descriptor writer composes the content. The\n")
  append("# macro derives the descriptor row of each content module from the bridge index of the half, so `descriptors`\n")
  append("# states only the other rows. Every target is `manual`, which the macros add.\n")
  if (plans.isEmpty()) {
    return@buildString
  }
  append("\n")
  // The bridge of the half exports `dev_dist_product_descriptor` bound to its descriptor index. The load lines are in
  // the order buildifier sorts them: a file of an explicit repository before a file of this one.
  val loads = ArrayList<LoadStatement>()
  if (plans.any(ProductDescriptorPlan::hasApplicationInfo)) {
    loads.add(LoadStatement(index.planLabel(PRODUCT_DESCRIPTOR_RULE), listOf("dev_dist_product_application_info")))
  }
  loads.add(LoadStatement("@${half.jpsBridge}//:targets.bzl", listOf("dev_dist_product_descriptor")))
  loads.add(LoadStatement(DEV_DIST_MODULE_SETS_BZL, listOf(DEV_DIST_MODULE_SETS_SYMBOL)))
  val loadOrder = BazelLabelComparator(forLoadStatements = true)
  for (load in loads.sortedWith { a, b -> loadOrder.compare(a.bzlFile, b.bzlFile) }) {
    append(load.render()).append("\n")
  }
  for (plan in plans.sortedBy(ProductDescriptorPlan::name)) {
    append("\n")
    // `name` first, then the attributes in buildifier's alphabetical order, so a regenerate leaves the file unchanged.
    val attributes = TreeMap(plan.content.attributes(DEV_DIST_MODULE_SETS_SYMBOL))
    if (plan.descriptors.isNotEmpty()) {
      attributes.put("descriptors", plan.descriptors.entries.associateTo(LinkedHashMap()) { index.planLabel(it.key) to it.value })
    }
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
    val descriptor = Target("dev_dist_product_descriptor")
    descriptor.option("name", "${plan.name}_product_descriptor")
    for ((key, value) in attributes) {
      descriptor.option(key, value)
    }
    append(descriptor.render())

    if (plan.hasApplicationInfo) {
      append("\n")
      val applicationInfo = Target("dev_dist_product_application_info")
      applicationInfo.option("name", "${plan.name}_application_info")
      applicationInfo.option("replacements", plan.replacements.unsorted())
      applicationInfo.option("source", index.planLabel(plan.applicationInfo))
      append(applicationInfo.render())
    }
  }
}

/** The `.bzl` file that exports `dev_dist_product_application_info`. */
private const val PRODUCT_DESCRIPTOR_RULE: String = "@community//platform/build-scripts/bazel-rules:dev_dist_product_descriptor.bzl"

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

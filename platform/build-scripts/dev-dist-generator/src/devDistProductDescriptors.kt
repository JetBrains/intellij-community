// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import java.nio.file.Files
import java.nio.file.Path

/**
 * The files of [PRODUCT_DESCRIPTOR_PACKAGE], keyed by their project-relative paths: the `BUILD.bazel` of the package and
 * the Product DSL content of every product in [plans].
 *
 * A package of its own, so a product in `//build` is not analysed again when the content of one product changes. A plan
 * keeps its labels in the recorded form, and the `BUILD.bazel` spells them for a package of the half of [index], see
 * [DevDistBazelIndex.planLabel].
 */
internal fun renderProductDescriptorPackage(plans: List<ProductDescriptorPlan>, index: DevDistBazelIndex): Map<String, String> {
  val result = LinkedHashMap<String, String>()
  result.put("$PRODUCT_DESCRIPTOR_PACKAGE/BUILD.bazel", renderProductDescriptorBuildFile(plans, index))
  for (plan in plans) {
    check(result.put(plan.sourceRelativePath, plan.content) == null) { "Two products write '${plan.sourceRelativePath}'" }
  }
  return result
}

private fun renderProductDescriptorBuildFile(plans: List<ProductDescriptorPlan>, index: DevDistBazelIndex): String = buildString {
  append(GENERATED_BY_HEADER)
  append("#\n")
  append("# The two generated entries of the application-info module jar of each product: the product descriptor and the\n")
  append("# stamped application info. `dev_dist_platform_jar` patches both outputs into the jar, see the `patches` of the\n")
  append("# residual jar in `build/dev_dist_fragment_inputs.bzl`.\n")
  append("#\n")
  append("# `<product>.xml` is the Product DSL content of the product with the module sets and the deprecated includes\n")
  append("# inlined. `processAndGetProductPluginContentModules` loads the same text, and the descriptor writer resolves it\n")
  append("# from the declared descriptors alone. Every target is `manual`, which the macros add.\n")
  if (plans.isEmpty()) {
    return@buildString
  }
  append("\n")
  append(LoadStatement(
    bzlFile = index.planLabel("@community//platform/build-scripts/bazel-rules:dev_dist_product_descriptor.bzl"),
    symbols = listOf("dev_dist_product_application_info", "dev_dist_product_descriptor"),
  ).render())
  append("\n")
  for (plan in plans.sortedBy(ProductDescriptorPlan::name)) {
    append("\n")
    // `name` first, then the attributes in buildifier's alphabetical order, so a regenerate leaves the file unchanged.
    val descriptor = Target("dev_dist_product_descriptor")
    descriptor.option("name", "${plan.name}_product_descriptor")
    if (plan.descriptors.isNotEmpty()) {
      descriptor.option("descriptors", plan.descriptors.entries.associateTo(LinkedHashMap()) { index.planLabel(it.key) to it.value })
    }
    if (plan.libraryDescriptors.isNotEmpty()) {
      descriptor.option("library_descriptors", plan.libraryDescriptors.entries.associateTo(LinkedHashMap()) { index.planLabel(it.key) to it.value })
    }
    descriptor.option("main_module", plan.mainModule)
    if (plan.refusedContentModules.isNotEmpty()) {
      descriptor.option("refused_content_modules", plan.refusedContentModules)
    }
    if (plan.scrambledContentModules.isNotEmpty()) {
      descriptor.option("scrambled_content_modules", plan.scrambledContentModules)
    }
    descriptor.option("source", plan.source)
    append(descriptor.render())
    append("\n")

    val applicationInfo = Target("dev_dist_product_application_info")
    applicationInfo.option("name", "${plan.name}_application_info")
    applicationInfo.option("product_code", plan.productCode)
    if (plan.replacements.isNotEmpty()) {
      applicationInfo.option("replacements", plan.replacements.unsorted())
    }
    applicationInfo.option("source", index.planLabel(plan.applicationInfo))
    append(applicationInfo.render())
  }
}

/** The `.xml` files of [PRODUCT_DESCRIPTOR_PACKAGE] that the run does not write, as project-relative paths. */
internal fun staleProductDescriptorSources(projectRoot: Path, written: Set<String>): List<String> {
  val directory = projectRoot.resolve(PRODUCT_DESCRIPTOR_PACKAGE)
  if (!Files.isDirectory(directory)) {
    return emptyList()
  }
  return Files.newDirectoryStream(directory, "*.xml").use { stream ->
    stream.map { "$PRODUCT_DESCRIPTOR_PACKAGE/${it.fileName}" }.filterNot { it in written }.sorted()
  }
}

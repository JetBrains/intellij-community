// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

/**
 * Writes one plugin's whole dev-distribution statement into the plugin's own `BUILD.bazel`.
 *
 * One section and one call, where two sections stood. The two halves restated the plugin's identity - 356 content targets
 * and 148 descriptor leaves named `descriptor_module`, so 504 statements answered 392 plugins - and a descriptor leaf
 * restated every fact of its plugin once per layout variant. `dev_dist_plugin` takes those facts once and expands to the
 * same leaves; its own docstring says what could not be joined, and why.
 *
 * A section of its own, and deliberately not `build`: both leaves are a function of the project model plus the plugin's
 * own `dev-dist.yaml` residue, and `bazel-targets.json` records both labels, so neither may disappear because a person
 * took over the module's compilation targets.
 *
 * Nothing is written when the plugin has neither half. The macro refuses a call that expands to nothing, and this is the
 * side that must not compose one.
 */
internal fun BuildFile.emitDevDistPlugin(module: ModuleDescriptor, content: PluginContent?, descriptors: List<PluginDescriptor>) {
  if (content == null && descriptors.isEmpty()) {
    return
  }

  load("@community//platform/build-scripts/bazel-rules:dev_dist_plugin.bzl", "dev_dist_plugin")
  val descriptor = commonPluginDescriptor(module = module, descriptors = descriptors)
  target("dev_dist_plugin") {
    // Emitted in the order the Starlark formatter sorts them - alphabetical - so that a regeneration needs no reformat.
    // The two halves interleave, which is why every line states which one it belongs to by taking `content` or
    // `descriptor`. Neither `name` nor `visibility`: each leaf macro derives its own name and defaults to public.
    content?.contentModuleLabels?.ifNotEmpty { option("content_modules", it) }
    descriptor?.descriptor?.ifNotEmpty { option("descriptor", it) }
    // The one fact both leaves take. The content leaf's target name is derived from it, and the descriptor leaf reads the
    // plugin's module name off the target it points at.
    option("descriptor_module", ":${module.targetName}")
    descriptor?.descriptors?.ifNotEmpty { option("descriptors", LinkedHashMap(it)) }
    descriptor?.directoryName?.ifNotEmpty { option("directory_name", it) }
    if (descriptor != null && !descriptor.embedContentModules) {
      option("embed_content_modules", false)
    }
    if (descriptor?.exactVersion == true) {
      option("exact_version", true)
    }
    content?.libraryContainerLabels?.ifNotEmpty { option("libraries", it) }
    descriptor?.libraryDescriptors?.ifNotEmpty { option("library_descriptors", LinkedHashMap(it)) }
    descriptor?.mainJarName?.ifNotEmpty { option("main_jar_name", it) }
    // Stated only with a descriptor leaf, whose *target name* carries it. A macro composes that name while Bazel loads,
    // so it can read the module name off no provider. The 254 content-only plugins carry no line for it.
    descriptor?.let { option("main_module", it.mainModule) }
    descriptor?.markers?.ifNotEmpty { option("markers", it) }
    content?.prepackedContentModuleLabels?.ifNotEmpty { option("prepacked_content_modules", it) }
    content?.prepackedJarDestinations?.ifNotEmpty { option("prepacked_jars", LinkedHashMap(it)) }
    descriptor?.refusedContentModules?.ifNotEmpty { option("refused_content_modules", it) }
    if (descriptor?.retainProductDescriptor == true) {
      option("retain_product_descriptor", true)
    }
    descriptor?.separateJar?.ifNotEmpty { option("separate_jar", it) }
    // Absent for the one variant that serves every platform, which is 135 of the 136 single-leaf plugins. The macro
    // answers an absent list with that variant, so nothing states the empty string.
    descriptors.map { it.variant }.filter { it.isNotEmpty() }.ifNotEmpty { option("variants", it) }
    descriptor?.versionSuffix?.ifNotEmpty { option("version_suffix", it) }
  }
}

/** Runs [action] unless this collection is empty, so that an attribute is written exactly where it says something. */
private inline fun <T : Collection<*>> T.ifNotEmpty(action: (T) -> Unit) {
  if (isNotEmpty()) {
    action(this)
  }
}

/** The same for a map attribute. */
private inline fun <T : Map<*, *>> T.ifNotEmpty(action: (T) -> Unit) {
  if (isNotEmpty()) {
    action(this)
  }
}

/** The same for a scalar whose empty value means "the convention answers it". */
private inline fun String.ifNotEmpty(action: (String) -> Unit) {
  if (isNotEmpty()) {
    action(this)
  }
}

/**
 * The one [PluginDescriptor] every layout variant of [module] agrees on, or `null` when [descriptors] is empty.
 *
 * `dev_dist_plugin.variants` is a list of variant tokens and not a table of per-variant deviations, because no plugin of
 * this project needs one: the two plugins that have variants - `intellij.jcef.plugin` and
 * `intellij.platform.daemon.plugin`, six variants each - state their six leaves identically apart from the token. Those
 * 12 leaves carried 120 lines of repeated attributes.
 *
 * A residue can still key a deviation by `<plugin>/<variant>`, so this **fails** rather than picks one. Taking the first
 * variant's section silently would drop a stated fact, and the whole purpose of the residue is that a stated fact
 * reaches the action.
 */
private fun commonPluginDescriptor(module: ModuleDescriptor, descriptors: List<PluginDescriptor>): PluginDescriptor? {
  val first = descriptors.firstOrNull() ?: return null
  for (other in descriptors) {
    val differing = other.deviatesFrom(first)
    check(differing.isEmpty()) {
      "${module.module.name}: layout variant '${other.variant}' states $differing differently from '${first.variant}'," +
      " and `dev_dist_plugin` states a plugin's descriptor facts once for every variant"
    }
  }
  return first
}

/** Which fields [other] states differently, apart from the layout variant itself. */
private fun PluginDescriptor.deviatesFrom(other: PluginDescriptor): List<String> {
  val differing = ArrayList<String>()
  fun compare(name: String, own: Any?, theirs: Any?) {
    if (own != theirs) {
      differing.add(name)
    }
  }
  compare("descriptor", descriptor, other.descriptor)
  compare("descriptors", descriptors, other.descriptors)
  compare("library_descriptors", libraryDescriptors, other.libraryDescriptors)
  compare("refused_content_modules", refusedContentModules, other.refusedContentModules)
  compare("separate_jar", separateJar, other.separateJar)
  compare("markers", markers, other.markers)
  compare("version_suffix", versionSuffix, other.versionSuffix)
  compare("directory_name", directoryName, other.directoryName)
  compare("main_jar_name", mainJarName, other.mainJarName)
  compare("embed_content_modules", embedContentModules, other.embedContentModules)
  compare("exact_version", exactVersion, other.exactVersion)
  compare("retain_product_descriptor", retainProductDescriptor, other.retainProductDescriptor)
  return differing
}

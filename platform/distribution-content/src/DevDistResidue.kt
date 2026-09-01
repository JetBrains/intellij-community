// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.distributionContent

import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * The modules a plugin's layout packs that the plugin's own `<content>` does not name, by plugin main module.
 *
 * A `PluginLayout.withModule` call and nothing else. The plugin descriptor does not name such a member and the project
 * model does not state it, so the JPS-to-Bazel converter writes it down. The rest of a plugin's dev-distribution
 * residue sits on the plugin's own `dev_dist_plugin` call in its `BUILD.bazel`, and this one field is central because
 * this is the one field a monorepo reader needs: nothing here runs the converter, and nothing here parses Starlark.
 *
 * The residue is a fact about one plugin and not about one product, so it names every member any product's layout
 * merges. A caller that reasons about one product gets a superset.
 *
 * `--write-dev-dist-residue --content-report=<zip>` is the one producer. The converter states the file's own format in
 * `readPluginExtraMembers` of
 * `community/platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/pluginContent.kt`. That generator is
 * the separate Bazel module `jps_to_bazel`, which takes the platform as published Maven artifacts, so no build compiles
 * the two together and the format is stated twice on purpose. A drift is loud rather than silent: the format is a
 * section header and a name per line, so a reader that no longer matches answers an empty list, and
 * `PatronusConfigYamlConsistencyTest` goes red - it compares the generated Patronus rules byte for byte, and the seeds
 * of every bundled plugin come from this table.
 */
@Internal
const val DEV_DIST_EXTRA_MEMBERS_FILE_NAME: String = "dev_dist_plugin_extra_members.txt"

/**
 * One plugin's remaining dev-distribution residue, beside the plugin's own main module.
 *
 * The merged members left this file for the table above, and the rest of the content residue is moving onto the
 * plugin's own `dev_dist_plugin` call. The name is still declared here because one reader outside the converter names
 * it: the orphan sweep of `contentRecipeOrphans.kt` claims the file. The descriptor deviations left for the
 * `plugin_descriptor_residue` section of `dev_dist_plugin_model_tables.txt`, so the converter is the one producer now.
 *
 * The name states the dev distribution, because `plugin-descriptor.yaml` is DevKit's documentation data for the plugin
 * descriptor format. `PluginDescriptorDocumentationTargetProvider` reads that file, and the SDK Docs page generator
 * publishes it. One file name for one concept lets the orphan sweep claim every file it names.
 */
@Internal
const val DEV_DIST_RESIDUE_FILE_NAME: String = "dev-dist.yaml"

private val cache = ConcurrentHashMap<Path, Map<String, List<String>>>()

/**
 * The whole table, parsed once per file.
 *
 * @param projectRoot the checkout root. The table lives under `community/build/`, so a community-only checkout and the
 *   monorepo both resolve it, and a checkout that has neither reads an empty table.
 */
@Internal
fun loadDevDistExtraMembers(projectRoot: Path): Map<String, List<String>> {
  val file = listOf(projectRoot.resolve("community/build"), projectRoot.resolve("build"))
                .map { it.resolve(DEV_DIST_EXTRA_MEMBERS_FILE_NAME) }
                .firstOrNull { it.exists() } ?: return emptyMap()
  return cache.computeIfAbsent(file, ::parseDevDistExtraMembers)
}

/** The merged members of one plugin, or an empty list where the layout merges none. */
@Internal
fun readDevDistExtraMembers(projectRoot: Path, pluginMainModule: String): List<String> {
  return loadDevDistExtraMembers(projectRoot).get(pluginMainModule) ?: emptyList()
}

/**
 * `[<plugin main module>]` opens a section, every other line is one member name, and a `#` line is a comment.
 *
 * A name above the first section header is an error rather than a name nobody owns.
 */
private fun parseDevDistExtraMembers(file: Path): Map<String, List<String>> {
  val result = LinkedHashMap<String, MutableList<String>>()
  var members: MutableList<String>? = null
  for (raw in file.readText().lineSequence()) {
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith('#')) {
      continue
    }
    if (line.startsWith('[') && line.endsWith(']')) {
      members = result.computeIfAbsent(line.substring(1, line.length - 1)) { ArrayList() }
      continue
    }
    val current = requireNotNull(members) {
      "$file states the member '$line' above the first `[<plugin main module>]` line, so no plugin owns it"
    }
    current.add(line)
  }
  return result
}

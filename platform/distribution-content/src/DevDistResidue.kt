// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.distributionContent

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * One plugin's dev-distribution residue, beside the plugin's own main module.
 *
 * Two parts and two producers. `plugin-model-tool` writes the `descriptor:` part, and the JPS-to-Bazel converter writes
 * the `content:` part. Each rewrites only its own key and keeps the other's verbatim, so the two never race for the
 * file. `DevDistResidueFile` in the converter holds the schema of both parts.
 *
 * The name states the dev distribution, because `plugin-descriptor.yaml` is DevKit's documentation data for the plugin
 * descriptor format. `PluginDescriptorDocumentationTargetProvider` reads that file, and the SDK Docs page generator
 * publishes it. One file name for one concept lets the orphan sweep claim every file it names.
 *
 * This is the one declaration of the name a monorepo module may read. The converter declares its own, because it is a
 * separate Bazel module that no monorepo target can compile against.
 */
@Internal
const val DEV_DIST_RESIDUE_FILE_NAME: String = "dev-dist.yaml"

/**
 * The part of a plugin's dev-distribution residue a reader outside the JPS-to-Bazel converter needs.
 *
 * The residue holds more, and the converter owns the whole model. `strictMode = false` lets this reader declare only
 * the one field it uses, so a new field elsewhere in the residue does not break it.
 *
 * The converter states the whole model in `ContentResidueSection` of
 * `community/platform/build-scripts/bazel/src/org/jetbrains/intellij/build/bazel/devDistResidue.kt`. That generator is
 * the separate Bazel module `jps_to_bazel`, which takes the platform as published Maven artifacts, so no build compiles
 * the two together and this narrow mirror is the only route. `strictMode = false` cuts both ways: a renamed serial name
 * decodes as the default here instead of failing, so `readDevDistExtraMembers` would answer an empty list for every
 * plugin. `PatronusConfigYamlConsistencyTest` is the gate. It compares the generated Patronus rules byte for byte, and
 * the seeds of every bundled plugin come from this field.
 */
@Serializable
private data class DevDistResidue(@JvmField val content: DevDistResidueContent? = null)

@Serializable
private data class DevDistResidueContent(
  /** Module names the plugin layout packs that the plugin's own `<content>` does not name. */
  @JvmField @SerialName("extra_members") val extraMembers: List<String> = emptyList(),
)

private val residueYaml = Yaml(configuration = YamlConfiguration(strictMode = false))

/**
 * The modules a plugin layout merges into the plugin's own jar, from the residue beside the plugin.
 *
 * A merged member is a `PluginLayout.withModule` call, and the plugin descriptor does not name it. So the project
 * model alone cannot state it, and the residue is where the converter records what the model cannot answer.
 *
 * The residue is a fact about one plugin and not about one product, so it names every member any product's layout
 * merges. A caller that reasons about one product gets a superset.
 *
 * @param contentRoots the content roots of the plugin's main module. The first one that holds a residue answers.
 */
@Internal
fun readDevDistExtraMembers(contentRoots: Iterable<Path>): List<String> {
  val residueFile = contentRoots.asSequence()
                      .map { it.resolve(DEV_DIST_RESIDUE_FILE_NAME) }
                      .firstOrNull { it.exists() } ?: return emptyList()
  return residueYaml.decodeFromString(DevDistResidue.serializer(), residueFile.readText()).content?.extraMembers ?: emptyList()
}

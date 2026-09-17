@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.devDist

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.SignNativeFileMode
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.getLibNameBySourceFile
import java.nio.file.Path

/** Identifies the original JarPackager source constructor, not a property of an archive's contents. */
@ApiStatus.Internal
enum class PluginSymbolicNativeSourceChannel {
  MODULE_OUTPUT,
  MODULE_JAR_LIBRARY,
  LAYOUT_LIBRARY,
  OPAQUE,
}

/** One source occurrence in the original assembly order. Repeated input identities remain separate occurrences. */
@ApiStatus.Internal
data class PluginSymbolicNativeSource(
  @JvmField val artifact: PluginSymbolicArtifact,
  @JvmField val channel: PluginSymbolicNativeSourceChannel,
)

/** Native policy from the selected distribution. Target platforms do not disable inline archive signing. */
@ApiStatus.Internal
data class PluginSymbolicNativePolicy(
  @JvmField val handlerEnabled: Boolean,
  @JvmField val macSigningEnabled: Boolean,
  @JvmField val signingMode: SignNativeFileMode,
  @JvmField val presignedLibraries: Map<String, String>,
  @JvmField val targetOs: List<OsFamily>,
  @JvmField val targetArch: JvmArchitecture?,
)

@ApiStatus.Internal
enum class PluginSymbolicNativeHandling {
  UNTOUCHED,
  INLINE_SIGNING,
  PRESIGNED_EXTRACTION,
}

/**
 * A requirement at one original source position, not an executable preparation or a native entry inventory.
 * Only [PluginSymbolicNativeHandling.UNTOUCHED] proves that native handling cannot change the source.
 * Other results require Kotlin preparation to apply filters, duplicate precedence, and byte-dependent decisions.
 * Different occurrences of one [inputId] can require different preparations. Bind them by source occurrence, not raw input alone.
 * [modelSignature] identifies this selection policy, not signing inputs or the prepared output bytes.
 */
@ApiStatus.Internal
data class PluginSymbolicNativeRequirement(
  @JvmField val sourceIndex: Int,
  @JvmField val inputId: String,
  @JvmField val handling: PluginSymbolicNativeHandling,
  @JvmField val distributionPrefix: String?,
  @JvmField val modelSignature: String,
)

/**
 * Follows layoutDistribution, createModuleSource, packLibFilesIntoModuleJar, and filesToSourceWithMapping.
 * Module directories bypass native handling. Module output archives are never extraction candidates.
 * Libraries inside module jars use the product's presigned-library map in both plugin and platform layouts.
 * Other layout libraries use that map only in the platform root.
 * An opaque or lazy source needs its original source policy before this helper can classify it.
 */
@ApiStatus.Internal
fun derivePluginSymbolicNativeRequirements(
  layout: BaseLayout,
  sources: List<PluginSymbolicNativeSource>,
  policy: PluginSymbolicNativePolicy?,
): List<PluginSymbolicNativeRequirement> {
  val nativePolicy = requireNotNull(policy) { "The selected distribution has no native policy" }
  require(layout is PluginLayout || layout is PlatformLayout) { "The original layout has no known native source policy" }
  return sources.mapIndexed { index, source ->
    val artifact = source.artifact
    require(artifact.id.isNotBlank()) { "A native source requires an input identity" }
    require(artifact.fileName.isNotBlank() && artifact.fileName !in listOf(".", "..") && artifact.fileName.none { it in "/\\\u0000" }) {
      "Native source '${artifact.id}' requires its original file name"
    }
    require(source.channel != PluginSymbolicNativeSourceChannel.OPAQUE) {
      "Native source '${artifact.id}' has an opaque or lazy source policy; declare its original sources first"
    }
    require(artifact.kind == "archive" || artifact.kind == "directory" && source.channel == PluginSymbolicNativeSourceChannel.MODULE_OUTPUT) {
      "Native source '${artifact.id}' has an unsupported root kind '${artifact.kind}'"
    }
    val extractionCandidate = source.channel == PluginSymbolicNativeSourceChannel.MODULE_JAR_LIBRARY ||
                              layout is PlatformLayout && source.channel == PluginSymbolicNativeSourceChannel.LAYOUT_LIBRARY
    val library = if (nativePolicy.handlerEnabled && artifact.kind == "archive" && extractionCandidate) {
      getLibNameBySourceFile(Path.of(artifact.fileName))
    }
    else null
    val extraction = library != null && nativePolicy.presignedLibraries.containsKey(library)
    val handling = when {
      !nativePolicy.handlerEnabled || artifact.kind == "directory" -> PluginSymbolicNativeHandling.UNTOUCHED
      extraction -> PluginSymbolicNativeHandling.PRESIGNED_EXTRACTION
      nativePolicy.macSigningEnabled && nativePolicy.signingMode == SignNativeFileMode.ENABLED -> PluginSymbolicNativeHandling.INLINE_SIGNING
      else -> PluginSymbolicNativeHandling.UNTOUCHED
    }
    val distributionPrefix = if (extraction) "lib/${nativePolicy.presignedLibraries.getValue(checkNotNull(library))}/" else null
    PluginSymbolicNativeRequirement(
      sourceIndex = index,
      inputId = artifact.id,
      handling = handling,
      distributionPrefix = distributionPrefix,
      modelSignature = nativeSelectionSignature(source, handling, library, distributionPrefix, nativePolicy),
    )
  }
}

private fun nativeSelectionSignature(
  source: PluginSymbolicNativeSource,
  handling: PluginSymbolicNativeHandling,
  library: String?,
  distributionPrefix: String?,
  policy: PluginSymbolicNativePolicy,
): String {
  return devDistSignature {
    putInt(1)
    putString(source.channel.name)
    putString(source.artifact.kind)
    putString(handling.name)
    if (handling == PluginSymbolicNativeHandling.PRESIGNED_EXTRACTION) {
      putString(checkNotNull(library))
      putString(checkNotNull(distributionPrefix))
      putString(policy.signingMode.name)
      val platforms = policy.targetOs.map { it.name }.distinct().sorted()
      putInt(platforms.size)
      for (platform in platforms) putString(platform)
      putString(policy.targetArch?.name.orEmpty())
    }
  }
}

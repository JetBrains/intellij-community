// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.ArrayDeque
import kotlin.io.path.invariantSeparatorsPathString

private const val DEV_BUILD_COMPOSITION_SPEC_VERSION = 1

@Serializable
@ApiStatus.Internal
data class DevBuildCompositionComponent(
  /**
   * The component's tree, or `null` for a component whose manifest names where each of its files' bytes already are.
   *
   * See `DevBuildComponentEntry.source`: a producer of nothing but placements for already-packed jars declares no tree,
   * so there is none to name here.
   */
  @JvmField val root: String? = null,
  @JvmField val manifest: String,
  @JvmField val pluginClasspathPart: String? = null,
)

@Serializable
@ApiStatus.Internal
data class DevBuildCompositionSpec(
  @JvmField val version: Int = DEV_BUILD_COMPOSITION_SPEC_VERSION,
  @JvmField val expectedFragments: List<String>,
  /**
   * The plugin modules the distribution declares it contains, for `DevIdeConfig`.
   *
   * Stated by the distribution rather than summed over [components] on purpose: a module the product bundles is packed
   * by a plugin fragment several distributions share, and that fragment cannot know which of them asked for it. The
   * composer checks this against what the components report, so the two cannot drift apart silently.
   */
  @JvmField val additionalModules: List<String> = emptyList(),
  @JvmField val components: List<DevBuildCompositionComponent>,
  @JvmField val pluginClasspathPrefix: String? = null,
  @JvmField val sourceRunfiles: Map<String, String>? = null,
  @JvmField val sourceDirectoryRunfiles: Map<String, String> = emptyMap(),
  @JvmField val sourceBindings: String? = null,
)

private val compositionSpecJson = Json { ignoreUnknownKeys = false }

@Serializable
private data class DevBuildSourceArtifact(
  @JvmField val component: String,
  @JvmField val source: String,
  @JvmField val anchorRelativePath: String,
  @JvmField val type: String,
  @JvmField val members: List<String>,
)

private data class DevBuildBoundSource(
  @JvmField val path: Path,
  @JvmField val directory: Path?,
  @JvmField val type: String,
)

@ApiStatus.Internal
class DevBuildComponentSources private constructor(private val sources: Map<String, DevBuildBoundSource>) {
  private val directoryMembers = HashMap<String, HashSet<String>>().apply {
    for (source in sources.values) {
      val directory = source.directory?.toString() ?: continue
      val members = getOrPut(directory) { HashSet() }
      members.add(directory)
      members.add(source.path.toString())
    }
  }

  internal fun directory(source: Path): Path? = sources.get(source.toAbsolutePath().normalize().toString())?.directory

  internal fun resolve(source: Path, symlink: Boolean): Path {
    val absoluteSource = source.toAbsolutePath().normalize()
    val bound = checkNotNull(sources.get(absoluteSource.toString())) { "Missing declared artifact binding for $source" }
    val path = bound.path
    val directory = bound.directory
    if (directory != null) {
      check(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && directory.toRealPath() == directory) {
        "Declared source directory escapes its artifact binding: $directory"
      }
      check(path != directory && path.parent.toRealPath() == path.parent && path.parent.startsWith(directory)) {
        "Declared source member has an escaping directory alias: $source"
      }
    }
    if (symlink) {
      check(Files.isSymbolicLink(path)) { "Declared source member is not a symbolic link: $source" }
      val resolved = resolveLink(path, checkNotNull(directory) { "Missing symbolic link directory binding: $source" })
      check(if (bound.type == "directory") Files.isDirectory(resolved) else Files.isRegularFile(resolved)) {
        "Declared source member differs from its bound type: $source"
      }
    }
    else {
      check(bound.type == "file" && Files.isRegularFile(path) && (directory == null || !Files.isSymbolicLink(path))) {
        "Declared source member is not a regular file: $source"
      }
    }
    if (symlink && bound.type == "directory" && !Files.isSymbolicLink(source)) {
      for ((memberPath, binding) in sources) {
        val member = Path.of(memberPath)
        if (member != absoluteSource && member.startsWith(absoluteSource) && binding.type == "file") {
          val physicalMember = resolveLink(binding.path, checkNotNull(directory))
          check(Files.isRegularFile(physicalMember)) { "Declared source member differs from its bound type: $member" }
          check(member.toRealPath() == physicalMember) { "Staged source differs from its declared artifact binding: $member" }
        }
      }
    }
    else {
      check(source.toRealPath() == path.toRealPath()) { "Staged source differs from its declared artifact binding: $source" }
    }
    return if (symlink) path else path.toRealPath()
  }

  private fun resolveLink(path: Path, directory: Path): Path {
    val members = checkNotNull(directoryMembers.get(directory.toString())) { "Missing symbolic link directory binding: $path" }
    val pending = ArrayDeque(directory.relativize(path).toList())
    var resolved = directory
    var links = 0
    while (pending.isNotEmpty()) {
      val next = resolved.resolve(pending.removeFirst()).normalize()
      check(next.startsWith(directory)) { "Declared source symbolic link escapes its directory: $path" }
      check(next.toString() in members) { "Declared source symbolic link has an unbound member spelling: $next" }
      if (Files.isSymbolicLink(next)) {
        check(++links <= 40) { "Declared source symbolic link has too many links: $path" }
        val target = Files.readSymbolicLink(next)
        check(!target.isAbsolute) { "Declared source symbolic link escapes its directory: $path" }
        resolved = next.parent
        for (part in target.toList().asReversed()) pending.addFirst(part)
      }
      else {
        resolved = next
      }
    }
    return resolved.toRealPath().also {
      check(it.startsWith(directory)) { "Declared source symbolic link escapes its directory: $path" }
    }
  }

  companion object {
    internal fun read(file: Path, components: List<DevBuildCompositionComponent>): Map<String, DevBuildComponentSources> {
      val logicalAnchor = file.toAbsolutePath().normalize().parent
      val physicalAnchor = file.toRealPath().parent
      val componentSources = LinkedHashMap<String, MutableMap<String, DevBuildBoundSource>>()
      for (component in components) {
        check(componentSources.put(component.manifest, LinkedHashMap()) == null) { "Duplicate component manifest: ${component.manifest}" }
      }
      val roots = HashMap<String, MutableSet<String>>()
      for (line in Files.readAllLines(file)) {
        val artifact = compositionSpecJson.decodeFromString(DevBuildSourceArtifact.serializer(), line)
        val sources = checkNotNull(componentSources.get(artifact.component)) { "Unknown source binding component: ${artifact.component}" }
        val source = Path.of(artifact.source)
        check(artifact.source.isNotBlank() &&
              (source.toString() == artifact.source || source.invariantSeparatorsPathString == artifact.source) &&
              source.none { it.toString() == "." || it.toString() == ".." }) {
          "Unsafe source artifact path: ${artifact.source}"
        }
        val relative = Path.of(artifact.anchorRelativePath)
        check(!relative.isAbsolute && logicalAnchor.resolve(relative).normalize() == source.toAbsolutePath().normalize()) {
          "Source artifact disagrees with its binding anchor: ${artifact.source}"
        }
        val root = source.toAbsolutePath().normalize()
        check(roots.getOrPut(artifact.component) { HashSet() }.add(devBuildPathIdentity(root.toString()))) {
          "Duplicate source artifact binding: ${artifact.source}"
        }
        val physical = physicalAnchor.resolve(relative).normalize()
        if (artifact.type == "directory") {
          validateDevBuildDirectorySpellings(artifact.members)
          val memberIdentities = HashSet<String>()
          val directories = LinkedHashSet<Path>()
          for (member in artifact.members) {
            validateDevBuildLocalPath(member)
            check(memberIdentities.add(devBuildPathIdentity(member))) { "Duplicate source member binding: $member" }
            val memberPath = Path.of(member)
            check(sources.put(root.resolve(memberPath).toString(), DevBuildBoundSource(physical.resolve(memberPath), physical, "file")) == null) {
              "Overlapping source member binding: $member"
            }
            var parent = memberPath.parent
            while (parent != null) {
              directories.add(parent)
              parent = parent.parent
            }
          }
          for (directory in directories) {
            check(sources.put(root.resolve(directory).toString(), DevBuildBoundSource(physical.resolve(directory), physical, "directory")) == null) {
              "Source directory conflicts with a member binding: $directory"
            }
          }
        }
        else {
          check(artifact.type == "file" && artifact.members.isEmpty()) { "Unsupported source artifact type: ${artifact.type}" }
          check(sources.put(root.toString(), DevBuildBoundSource(physical, null, artifact.type)) == null) { "Overlapping source artifact binding: $source" }
        }
      }
      return componentSources.mapValues { DevBuildComponentSources(it.value) }
    }
  }
}

@ApiStatus.Internal
fun readDevBuildSourceBindings(file: Path, components: List<DevBuildCompositionComponent>): Map<String, DevBuildComponentSources> {
  return DevBuildComponentSources.read(file, components)
}

@ApiStatus.Internal
fun readDevBuildCompositionSpec(file: Path): DevBuildCompositionSpec {
  val spec = compositionSpecJson.decodeFromString(DevBuildCompositionSpec.serializer(), Files.readString(file))
  check(spec.version == DEV_BUILD_COMPOSITION_SPEC_VERSION) {
    "Unsupported dev-build composition spec version ${spec.version} in $file"
  }
  check(spec.components.isNotEmpty()) { "Dev-build composition spec in $file has no components" }
  return spec
}

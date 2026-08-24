// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

internal const val PROVIDED_SUFFIX = "-provided"

internal data class LibraryContainer(
  @JvmField val repoLabel: String,
  @JvmField val buildFile: Path,
  @JvmField val moduleFile: Path,
  @JvmField val visibility: String? = "//visibility:public",
  @JvmField val sectionName: String = "maven libs",
  @JvmField val isCommunity: Boolean,
)

internal data class LibraryTarget(
  @JvmField val jpsName: String,
  @JvmField val targetName: String,
  @JvmField val container: LibraryContainer,
  @JvmField val moduleLibraryModuleName: String?,
)

internal sealed interface Library {
  val target: LibraryTarget
}

@Suppress("unused")
internal data class MavenLibrary(
  @JvmField val mavenCoordinates: String,
  @JvmField val jars: List<MavenFileDescription>,
  @JvmField val sourceJars: List<MavenFileDescription>,
  @JvmField val javadocJars: List<MavenFileDescription>,
  override val target: LibraryTarget,
) : Library

internal data class MavenCoordinates(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val classifier: String? = null,
  val packaging: String = ".jar",
) {
  init {
    require(groupId.isNotBlank())
    require(artifactId.isNotBlank())
    require(version.isNotBlank())
    require(classifier == null || classifier.isNotBlank())
    require(packaging.isNotBlank())
  }
}

internal data class MavenFileDescription(
  val mavenCoordinates: MavenCoordinates,
  val path: Path,
  val sha256checksum: String?,
)

internal data class LocalLibrary(
  val files: List<Path>,
  val bazelBuildFileDir: Path,
  override val target: LibraryTarget,
) : Library

private fun getUrlAndSha256(jar: MavenFileDescription, jarRepositories: List<JarRepository>, m2Repo: Path, urlCache: UrlCache): CacheEntry {
  val jarPath = jar.path.relativeTo(m2Repo).invariantSeparatorsPathString
  val entry = urlCache.getEntry(jarPath)
  if (entry == null) {
    println("Resolving: $jarPath")
    for (repo in jarRepositories) {
      val url = "${repo.url}/${jarPath}"
      if (urlCache.checkUrl(url, repo)) {
        val hash = urlCache.calculateHash(url, repo)
        check(jar.sha256checksum == null || hash == jar.sha256checksum) {
          "Hash mismatch: got ${jar.sha256checksum} from .idea/libraries, but ${hash} from downloading $url for ${jar.path}"
        }

        return urlCache.putUrl(jarPath = jarPath, url = url, hash = hash)
      }
    }
    error("Cannot find $jar in $jarRepositories (jarPath=$jarPath)")
  }
  check(jar.sha256checksum == null || entry.sha256 == jar.sha256checksum) {
    "Hash mismatch: got ${jar.sha256checksum} from .idea/libraries, but ${entry.sha256} for ${jar.path} from lib/MODULE.bazel or community/lib/MODULE.bazel"
  }
  return entry
}

internal fun checkLibraryFileWasAlreadyRendered(targetName: String, libVisibility: String?, hasSourceJar: Boolean, labelTracker: MutableMap<String, String>): Boolean {
  val trackerContent = "library_file has_source_jar:$hasSourceJar lib_visibility:$libVisibility"
  val olderContent = labelTracker.put(targetName, trackerContent)
  if (olderContent != null && olderContent != trackerContent) {
    // was rendered earlier, but the context was different
    error(
      "The same library target $targetName was rendered twice with different contexts: '$olderContent' and '$trackerContent'. " +
      "This is no allowed, please make sure that your library settings (e.g. having sources or visiblity) is consistent across all " +
      "library usages"
    )
  }

  return olderContent != null
}

internal fun BuildFile.generateMavenLib(
  lib: MavenLibrary,
  labelTracker: MutableMap<String, String>,
  isLibraryProvided: (Library) -> Boolean,
  libVisibility: String?,
) {
  val targetName = lib.target.targetName
  @Suppress("SpellCheckingInspection")
  if (targetName == "bifurcan" || targetName == "kotlinx-collections-immutable-jvm") {
    return
  }

  for (jar in lib.jars) {
    // We must use path with '/' (groupDirectory=true) because file name on disk is used to match library files
    // in org.jetbrains.intellij.build.impl.JarPackagerKt.getCanonicalPath
    val label = mavenCoordinatesToFileName(jar.mavenCoordinates, groupDirectory = true)
    if (labelTracker.put(label, "") != null) {
      continue
    }

    // It would be better to use alias, but alias does not propagate to runfiles
    // see https://github.com/bazelbuild/bazel/issues/18477
    load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
    target("copy_file") {
      option("name", label + "_copy")
      option("src", "@${fileToHttpRuleFile(jar.mavenCoordinates)}")
      option("out", label)
      option("allow_symlink", true)
      visibility(arrayOf("//visibility:public"))
    }
  }

  if (lib.jars.size == 1) {
    val jar = lib.jars.single()
    val sourceJar = lib.sourceJars.firstOrNull { it.mavenCoordinates == jar.mavenCoordinates.copy(classifier = "sources") }

    if (checkLibraryFileWasAlreadyRendered(targetName, libVisibility, sourceJar != null, labelTracker)) {
      return
    }

    target("jvm_import") {
      option("name", targetName)
      if (targetName == "kotlinx-serialization-core") {
        option("exported_compiler_plugins", listOf("@lib//:kotlin-serialization-plugin"))
      }
      option("jar", "@${fileToHttpRuleFile(jar.mavenCoordinates)}")
      if (sourceJar != null) {
        option("source_jar", "@${fileToHttpRuleFile(sourceJar.mavenCoordinates)}")
      }
      libVisibility?.let {
        visibility(arrayOf(it))
      }
    }
  }
  else {
    load("@rules_java//java:defs.bzl", "java_library")
    target("java_library") {
      option("name", targetName)
      libVisibility?.let {
        visibility(arrayOf(it))
      }
      option("exports", lib.jars.map {
        ":${mavenCoordinatesToHttpRuleRepoName(it.mavenCoordinates)}_import"
      }.unsorted())
    }

    for (jar in lib.jars) {
      val bazelLabel = mavenCoordinatesToHttpRuleRepoName(jar.mavenCoordinates)
      val label = "${bazelLabel}_import"
      val sourceJar = lib.sourceJars.firstOrNull { it.mavenCoordinates == jar.mavenCoordinates.copy(classifier = "sources") }

      if (checkLibraryFileWasAlreadyRendered(label, libVisibility, sourceJar != null, labelTracker)) {
        continue
      }

      target("jvm_import") {
        option("name", label)
        option("jar", "@$bazelLabel//file")
        if (sourceJar != null) {
          option("source_jar", "@${fileToHttpRuleFile(sourceJar.mavenCoordinates)}")
        }
      }
    }
  }

  if (isLibraryProvided(lib)) {
    generateProvidedMavenLib(lib = lib, libVisibility = libVisibility)
  }
}

internal fun BuildFile.generateProvidedMavenLib(
  lib: MavenLibrary,
  libVisibility: String?,
  targetContainer: LibraryContainer? = null,
) {
  val targetName = lib.target.targetName
  @Suppress("SpellCheckingInspection")
  if (targetName == "bifurcan" || targetName == "kotlinx-collections-immutable-jvm") {
    return
  }

  val exportedCompilerPlugins = when (targetName) {
    "kotlinx-serialization-core" -> listOf("@lib//:kotlin-serialization-plugin")
    else -> emptyList()
  }

  val exportsLabel = if (targetContainer == null) {
    ":$targetName"
  }
  else {
    "${targetContainer.repoLabel}//:$targetName"
  }

  if (exportedCompilerPlugins.isEmpty()) {
    target("java_library") {
      option("name", targetName + PROVIDED_SUFFIX)
      option("neverlink", true)
      libVisibility?.let {
        visibility(arrayOf(it))
      }
      option("exports", listOf(exportsLabel))
    }
  }
  else {
    load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
    target("kt_jvm_library") {
      option("name", targetName + PROVIDED_SUFFIX)
      option("exported_compiler_plugins", exportedCompilerPlugins)
      option("neverlink", true)
      libVisibility?.let {
        visibility(arrayOf(it))
      }
      option("exports", listOf(exportsLabel))
    }
  }
}

@Suppress("DuplicatedCode")
internal fun generateBazelModuleSectionsForLibs(
  list: List<MavenLibrary>,
  owner: LibraryContainer,
  jarRepositories: List<JarRepository>,
  m2Repo: Path,
  urlCache: UrlCache,
  moduleFileToLabelTracker: MutableMap<Path, MutableMap<String, String>>,
  fileToUpdater: MutableMap<Path, BazelFileUpdater>,
) {
  val bazelFileUpdater = fileToUpdater.computeIfAbsent(owner.moduleFile) {
    val updater = BazelFileUpdater(it)
    updater.removeSections("maven-libs")
    updater.removeSections("maven libs")
    updater
  }

  val labelTracker = moduleFileToLabelTracker.computeIfAbsent(owner.moduleFile) { mutableMapOf() }
  buildFile(bazelFileUpdater, owner.sectionName) {
    for (lib in list) {
      for (jar in lib.jars) {
        val label = mavenCoordinatesToHttpRuleRepoName(jar.mavenCoordinates)
        if (labelTracker.put(label, "") != null) {
          continue
        }

        check(!jar.sha256checksum.isNullOrBlank()) {
          "COMPILE library root ${jar.path} must have a checksum"
        }

        val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = urlCache)
        target("http_file") {
          option("name", label)
          option("downloaded_file_path", jar.path.fileName.name)
          option("sha256", entry.sha256)
          option("url", entry.url)
        }
      }

      for (jar in lib.sourceJars) {
        val label = mavenCoordinatesToHttpRuleRepoName(jar.mavenCoordinates)
        if (labelTracker.put(label, "") != null) {
          continue
        }

        if (jar.path.isDirectory()) {
          // manually attached source directory
          println("WARN: source directory ${jar.path} is attached to ${lib.target.jpsName}, not generating anything out of it")
          continue
        }

        val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = urlCache)
        target("http_file") {
          option("name", label)
          option("downloaded_file_path", jar.path.fileName.name)
          option("sha256", entry.sha256)
          option("url", entry.url)
        }
      }
    }
  }
}

/**
 * We need to use this format to avoid clashes:
 * ```
 * [groupId]-[artifactId]-[version]-[jarFileName]
 * ```
 *
 * We can't use only the filename, as there can be non-unique filenames in different GAV coordinates; we can't only use the coordinates, as there are transitive dependencies in
 * many JPS Maven library entries.
 *
 * To reduce noise, we can remove duplication of the GAV coordinate parts from the jar filename, and then make sure there are no consecutive dashes left over.
 */
private fun mavenCoordinatesToHttpRuleRepoName(mavenCoordinates: MavenCoordinates): String {
  val name = mavenCoordinatesToFileName(mavenCoordinates, groupDirectory = false).removeSuffix(".jar")
  val sanitizedName = bazelLabelBadCharsPattern.replace(name, "_")
  return sanitizedName + "_http"
}

/**
 * The Bazel label of the target that groups [library]'s jars - the `jvm_import`, `java_library` or `java_import`
 * generated by [generateMavenLib] or [generateLocalLibs].
 *
 * This is the label a module already names in its `deps`/`runtime_deps`/`exports`, so it carries no version: a library
 * version bump rewrites the jar file names, and therefore every label [libraryJarTargets] produces, but leaves this one
 * alone. Consumers that merge the jars resolve them from the target's `JavaInfo` instead.
 *
 * Never returns the `-provided` variant: that target is `neverlink`, so it carries no runtime jars. A dependency label,
 * which may need it, goes through [libraryDependencyLabel] instead - both compute the package the same way.
 */
internal fun libraryTargetLabel(library: Library, communityRoot: Path, ultimateRoot: Path?, isCommunityDependent: Boolean): String {
  return libraryPackageLabel(
    library = library,
    container = library.target.container,
    communityRoot = communityRoot,
    ultimateRoot = ultimateRoot,
    isCommunityDependent = isCommunityDependent,
  ) + ":" + library.target.targetName
}

/**
 * The label a module's `deps`/`runtime_deps`/`exports` names [library] by.
 *
 * [libraryTargetLabel] with the two things a dependency edge adds: the `-provided` variant, whose target is
 * `neverlink`, and [container], which for a provided Maven library is the repository the `-provided` target was
 * generated into rather than the one the library itself landed in.
 */
internal fun libraryDependencyLabel(
  library: Library,
  container: LibraryContainer,
  communityRoot: Path,
  ultimateRoot: Path?,
  isCommunityDependent: Boolean,
  isProvided: Boolean,
): String {
  val packageLabel = libraryPackageLabel(
    library = library,
    container = container,
    communityRoot = communityRoot,
    ultimateRoot = ultimateRoot,
    isCommunityDependent = isCommunityDependent,
  )
  return packageLabel + ":" + library.target.targetName + (if (isProvided) PROVIDED_SUFFIX else "")
}

/**
 * The Bazel package [library]'s targets are generated into, as a repository-qualified label with no target name.
 *
 * Computed in one place because it is written twice - once as the target label a jar consumer resolves, once as the
 * dependency label a module's `BUILD.bazel` carries - and the two silently packing different jars is exactly what a
 * second copy of these four cases produces.
 *
 * [isCommunityDependent] is the repo of the module the label is written into. It only matters for a local library that
 * lives under the community root but outside `community/lib`, where community writes `//` and ultimate `@community//` -
 * the same distinction [generateDeps] makes.
 */
private fun libraryPackageLabel(
  library: Library,
  container: LibraryContainer,
  communityRoot: Path,
  ultimateRoot: Path?,
  isCommunityDependent: Boolean,
): String {
  return when (library) {
    is MavenLibrary -> "${container.repoLabel}//"

    is LocalLibrary -> {
      val dir = library.bazelBuildFileDir
      val communityLibRoot = communityRoot.resolve("lib")
      val ultimateLibRoot = ultimateRoot?.resolve("lib")
      when {
        dir.startsWith(communityLibRoot) -> "@lib//${dir.relativeTo(communityLibRoot).invariantSeparatorsPathString}"
        ultimateLibRoot != null && dir.startsWith(ultimateLibRoot) -> "@ultimate_lib//${dir.relativeTo(ultimateLibRoot).invariantSeparatorsPathString}"
        dir.startsWith(communityRoot) ->
          "${if (isCommunityDependent) "//" else "@community//"}${dir.relativeTo(communityRoot).invariantSeparatorsPathString}"
        ultimateRoot != null && dir.startsWith(ultimateRoot) -> "//${dir.relativeTo(ultimateRoot).invariantSeparatorsPathString}"
        else -> error("Unknown library root location: $dir (community=$communityRoot, ultimate=$ultimateRoot)")
      }
    }
  }
}

/**
 * The Bazel label of every jar of [library], in the order the library declares them.
 *
 * One jar, one label - a merging packer needs the individual files, not the `jvm_import`/`java_import` target that
 * groups them, and the order is load-bearing: the distribution packer resolves duplicate entries by first source, so
 * a reordered jar list changes which copy of a duplicated entry ships.
 *
 * Maven jars are the `copy_file` outputs generated by [generateMavenLib]; local library jars are the `exports_files`
 * entries generated by [generateLocalLibs]. Both label forms are also what `build/bazel-targets.json` records as
 * `jarTargets`, so a consumer can cross-check either way.
 */
internal fun libraryJarTargets(library: Library, communityRoot: Path, ultimateRoot: Path?, projectRoot: Path): List<String> {
  return when (library) {
    is MavenLibrary -> library.jars.map { file ->
      library.target.container.repoLabel + "//:" + mavenCoordinatesToFileName(file.mavenCoordinates, groupDirectory = true)
    }

    is LocalLibrary -> library.files.map { file ->
      val normalized = file.normalize()
      require(normalized.startsWith(communityRoot) || (ultimateRoot != null && normalized.startsWith(ultimateRoot))) {
        "Library file $file is not under community root ($communityRoot) or ultimate root ($ultimateRoot)"
      }

      val ultimateLibRoot = ultimateRoot?.resolve("lib")
      val communityLibRoot = communityRoot.resolve("lib")
      when {
        ultimateLibRoot != null && normalized.startsWith(ultimateLibRoot) -> normalized.toBazelFileLabel("@ultimate_lib", ultimateLibRoot)
        normalized.startsWith(communityLibRoot) -> normalized.toBazelFileLabel("@lib", communityLibRoot)
        projectRoot == ultimateRoot && normalized.startsWith(communityRoot) -> normalized.toBazelFileLabel("@community", communityRoot)
        else -> normalized.toBazelFileLabel("", projectRoot)
      }
    }
  }
}

private fun Path.toBazelFileLabel(repoName: String, repoRoot: Path): String {
  require(startsWith(repoRoot)) { "Path $this is not under root $repoRoot" }
  require(normalize() == this) { "Path $this must be normalized" }
  require(repoName.startsWith("@") || repoName.isEmpty()) { "Repo name $repoName must start with '@' or be empty" }
  val relative = relativeTo(repoRoot)
  return "$repoName//${if (relative.parent == null) "" else relative.parent.invariantSeparatorsPathString}:${relative.fileName}"
}

internal fun mavenCoordinatesToFileName(mavenCoordinates: MavenCoordinates, groupDirectory: Boolean): String {
  val name = buildString {
    append(mavenCoordinates.groupId)

    if (groupDirectory) {
      append('/')
    }
    else {
      append('-')
    }

    append(mavenCoordinates.artifactId)
    append('-')
    append(mavenCoordinates.version)

    if (mavenCoordinates.classifier != null) {
      append('-')
      append(mavenCoordinates.classifier)
    }

    append(mavenCoordinates.packaging)
  }

  return name
}

internal fun fileToHttpRuleFile(coordinates: MavenCoordinates): String =
  mavenCoordinatesToHttpRuleRepoName(coordinates) + "//file"

internal fun generateLocalLibs(libs: Collection<LocalLibrary>, isLibraryProvided: (Library) -> Boolean, fileToUpdater: MutableMap<Path, BazelFileUpdater>) {
  for ((dir, libs) in libs.sortedBy { it.target.targetName }.groupBy { it.bazelBuildFileDir }) {
    val bazelFileUpdater = fileToUpdater.computeIfAbsent(dir.resolve("BUILD.bazel")) { BazelFileUpdater(it) }
    bazelFileUpdater.removeSections("local-libraries")
    val loadSymbols = mutableMapOf<String, MutableSet<String>>()
    buildFile(bazelFileUpdater, "local-libs") {
      loadSymbols.getOrPut("@rules_java//java:defs.bzl", ::mutableSetOf) += "java_import"
      for (lib in libs) {
        val targetName = lib.target.targetName
        target("java_import") {
          option("name", targetName)
          option("jars", lib.files.map {
            it.relativeTo(dir).invariantSeparatorsPathString
          })
          option("visibility", listOf("//visibility:public"))
        }

        if (isLibraryProvided(lib)) {
          loadSymbols.getOrPut("@rules_java//java:defs.bzl", ::mutableSetOf) += "java_library"
          target("java_library") {
            option("name", targetName + PROVIDED_SUFFIX)
            option("neverlink", true)
            visibility(arrayOf("//visibility:public"))
            option("exports", listOf(":$targetName"))
          }
        }

        for (file in lib.files) {
          exportFile(file.relativeTo(dir).invariantSeparatorsPathString)
        }
      }
    }
    bazelFileUpdater.appendLoadSymbols(loadSymbols)
  }
}

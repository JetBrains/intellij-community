// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
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
  @JvmField val isModuleLibrary: Boolean,
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

internal data class MavenFileDescription(
  @JvmField val path: Path,
  @JvmField val sha256checksum: String?,
)

internal data class LocalLibrary(
  @JvmField val files: List<Path>,
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

internal fun BuildFile.generateMavenLib(
  lib: MavenLibrary,
  labelTracker: MutableSet<String>,
  isLibraryProvided: (Library) -> Boolean,
  libVisibility: String?,
) {
  val targetName = lib.target.targetName
  @Suppress("SpellCheckingInspection")
  if (targetName == "bifurcan" || targetName == "kotlinx-collections-immutable-jvm") {
    return
  }

  var exportedCompilerPlugins = emptyList<String>()
  if (lib.jars.size == 1) {
    val jar = lib.jars.single()
    @Suppress("UnnecessaryVariable")
    val libName = targetName
    if (!labelTracker.add(libName)) {
      return
    }

    val sourceJar = lib.sourceJars.singleOrNull { it.path.name == "${jar.path.nameWithoutExtension}-sources.jar" }

    target("jvm_import") {
      option("name", targetName)
      option("jar", "@${fileToHttpRuleFile(jar.path)}")
      if (sourceJar != null) {
        option("source_jar", "@${fileToHttpRuleFile(sourceJar.path)}")
      }
      if (targetName == "kotlinx-serialization-core") {
        exportedCompilerPlugins = listOf("@lib//:kotlin-serialization-plugin")
        option("exported_compiler_plugins", exportedCompilerPlugins)
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
      option("exports", lib.jars.map { ":${fileToHttpRuleRepoName(it.path)}_import" })
      libVisibility?.let {
        visibility(arrayOf(it))
      }
    }

    for (jar in lib.jars) {
      val bazelLabel = fileToHttpRuleRepoName(jar.path)
      val label = "${bazelLabel}_import"
      if (!labelTracker.add(label)) {
        continue
      }

      val sourceJar = lib.sourceJars.singleOrNull { it.path.name == "${jar.path.nameWithoutExtension}-sources.jar" }
      target("jvm_import") {
        option("name", label)
        option("jar", "@$bazelLabel//file")
        if (sourceJar != null) {
          option("source_jar", "@${fileToHttpRuleFile(sourceJar.path)}")
        }
      }
    }
  }

  if (isLibraryProvided(lib)) {
    if (exportedCompilerPlugins.isEmpty()) {
      target("java_library") {
        option("name", targetName + PROVIDED_SUFFIX)
        option("exports", listOf(":$targetName"))
        option("neverlink", true)
        libVisibility?.let {
          visibility(arrayOf(it))
        }
      }
    }
    else {
      target("kt_jvm_library") {
        option("name", targetName + PROVIDED_SUFFIX)
        option("exports", listOf(":$targetName"))
        option("neverlink", true)
        option("exported_compiler_plugins", exportedCompilerPlugins)
        libVisibility?.let {
          visibility(arrayOf(it))
        }
      }
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
  moduleFileToLabelTracker: MutableMap<Path, MutableSet<String>>,
  fileToUpdater: MutableMap<Path, BazelFileUpdater>,
) {
  val bazelFileUpdater = fileToUpdater.computeIfAbsent(owner.moduleFile) {
    val updater = BazelFileUpdater(it)
    updater.removeSections("maven-libs")
    updater.removeSections("maven libs")
    updater
  }

  val labelTracker = moduleFileToLabelTracker.computeIfAbsent(owner.moduleFile) { HashSet() }
  buildFile(bazelFileUpdater, owner.sectionName) {
    for (lib in list) {
      for (jar in lib.jars) {
        val label = fileToHttpRuleRepoName(jar.path)
        if (!labelTracker.add(label)) {
          continue
        }

        check(!jar.sha256checksum.isNullOrBlank()) {
          "COMPILE library root ${jar.path} must have a checksum"
        }

        val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = urlCache)
        target("http_file") {
          option("name", label)
          option("url", entry.url)
          option("sha256", entry.sha256)
          option("downloaded_file_path", jar.path.fileName.name)
        }
      }

      for (jar in lib.sourceJars) {
        val label = fileToHttpRuleRepoName(jar.path)
        if (!labelTracker.add(label)) {
          continue
        }

        val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = urlCache)
        target("http_file") {
          option("name", label)
          option("url", entry.url)
          option("sha256", entry.sha256)
          option("downloaded_file_path", jar.path.fileName.name)
        }
      }
    }
  }
}

private fun fileToHttpRuleRepoName(jar: Path): String = bazelLabelBadCharsPattern.replace(jar.nameWithoutExtension, "_") + "_http"

private fun fileToHttpRuleFile(jar: Path): String = fileToHttpRuleRepoName(jar) + "//file"

internal fun generateLocalLibs(libs: Collection<LocalLibrary>, isLibraryProvided: (Library) -> Boolean, fileToUpdater: MutableMap<Path, BazelFileUpdater>) {
  for ((dir, libs) in libs.asSequence().sortedBy { it.target.targetName }.groupBy { it.files.first().parent }) {
    val bazelFileUpdater = fileToUpdater.computeIfAbsent(dir.resolve("BUILD.bazel")) { BazelFileUpdater(it) }
    bazelFileUpdater.removeSections("local-libraries")
    buildFile(bazelFileUpdater, "local-libs") {
      load("@rules_java//java:defs.bzl", "java_import")
      for (lib in libs) {
        val targetName = lib.target.targetName
        target("java_import") {
          option("name", targetName)
          option("jars", lib.files.map { it.fileName.toString() })
          option("visibility", listOf("//visibility:public"))
        }

        if (isLibraryProvided(lib)) {
          load("@rules_java//java:defs.bzl", "java_library")
          target("java_library") {
            option("name", targetName + PROVIDED_SUFFIX)
            option("exports", listOf(":$targetName"))
            option("neverlink", true)
            visibility(arrayOf("//visibility:public"))
          }
        }
      }
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

internal const val PROVIDED_SUFFIX = "-provided"

internal data class LibOwnerDescriptor(
  @JvmField val repoLabel: String,
  @JvmField val buildFile: Path,
  @JvmField val moduleFile: Path,
  @JvmField val visibility: String? = "//visibility:public",
  @JvmField val sectionName: String = "maven libs",
)

internal data class Library(
  @JvmField val targetName: String,
  @JvmField val owner: LibOwnerDescriptor,
)

internal sealed interface LibOwner {
  val lib: Library
}

@Suppress("unused")
internal data class MavenLibrary(
  @JvmField val mavenCoordinates: String,
  @JvmField val jars: List<Path>,
  @JvmField val sourceJars: List<Path>,
  @JvmField val javadocJars: List<Path>,
  override val lib: Library,
) : LibOwner

internal data class LocalLibrary(
  @JvmField val files: List<Path>,
  override val lib: Library,
) : LibOwner

private fun getUrlAndSha256(jar: Path, jarRepositories: List<JarRepository>, m2Repo: Path, urlCache: UrlCache): CacheEntry {
  val jarPath = jar.relativeTo(m2Repo).invariantSeparatorsPathString
  val entry = urlCache.getEntry(jarPath)
  if (entry == null) {
    println("Resolving: $jarPath")
    for (repo in jarRepositories) {
      val url = "${repo.url}/${jarPath}"
      if (urlCache.checkUrl(url, repo)) {
        return urlCache.putUrl(jarPath = jarPath, url = url, repo = repo)
      }
    }
    error("Cannot find $jar in $jarRepositories (jarPath=$jarPath)")
  }
  return entry
}

private fun isKotlinLib(jars: List<Path>): Boolean {
  for (file in jars) {
    ZipFile(file.toFile()).use { zipFile ->
      for (entry in zipFile.entries()) {
        if (entry.name.startsWith("META-INF/") && entry.name.endsWith("kotlin_module")) {
          return true
        }
      }
    }
  }

  return false
}

internal fun BuildFile.generateMavenLib(
  lib: MavenLibrary,
  labelTracker: MutableSet<String>,
  providedRequested: Set<LibOwner>,
  libVisibility: String?,
) {
  val targetName = lib.lib.targetName
  @Suppress("SpellCheckingInspection")
  if (targetName == "bifurcan" || targetName == "kotlinx-collections-immutable-jvm") {
    return
  }

  // 1. wire: ERROR: /Users/develar/projects/idea-push/plugins/bigdatatools/kafka/BUILD.bazel:31:12: Building plugins/bigdatatools/kafka/kafka-java.jar (1 source file) failed:
  // (Exit 1): java failed: error executing Javac command (from target //plugins/bigdatatools/kafka:kafka) ../rules_java~~toolchains~remotejdk21_macos_aarch64/bin/java
  // '--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED' '--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED' ... (remaining 19 arguments skipped)
  // error: cannot determine module name for ../ultimate_lib~~_repo_rules~wire-compiler-3_7_1_http/file/wire-compiler-3.7.1.jar
  // 2. jetbrains-annotations - ijar doesn't add JPMS
  val isJavaLib = !targetName.startsWith("jetbrains-annotations") && (targetName.startsWith("bigdatatools-") || targetName.startsWith("wire") || !isKotlinLib(lib.jars))

  var exportedCompilerPlugins = emptyList<String>()
  if (lib.jars.size == 1) {
    val jar = lib.jars.single()
    val libName = targetName
    if (!labelTracker.add(libName)) {
      return
    }

    val sourceJar = lib.sourceJars.singleOrNull { it.name == "${jar.nameWithoutExtension}-sources.jar" }

    if (isJavaLib) {
      target("java_import") {
        option("name", targetName)
        option("jars", arrayOf("@${fileToHttpRuleFile(jar)}"))
        if (sourceJar != null) {
          option("srcjar", "@${fileToHttpRuleFile(sourceJar)}")
        }
        libVisibility?.let {
          visibility(arrayOf(it))
        }
      }
    }
    else {
      target("kt_jvm_import") {
        option("name", targetName)
        option("jar", "@${fileToHttpRuleFile(jar)}")
        if (sourceJar != null) {
          option("srcjar", "@${fileToHttpRuleFile(sourceJar)}")
        }
        if (targetName == "kotlinx-serialization-core") {
          exportedCompilerPlugins = listOf("@lib//:kotlin-serialization-plugin")
          option("exported_compiler_plugins", exportedCompilerPlugins)
        }
        if (targetName == "rhizomedb-compiler-plugin") {
          //todo update to kotlin 2.1.0
          option("exported_compiler_plugins", arrayOf("@lib//:rhizomedb-plugin"))
        }

        libVisibility?.let {
          visibility(arrayOf(it))
        }
      }
    }
  }
  else {
    load("@rules_java//java:defs.bzl", "java_library")
    target("java_library") {
      option("name", targetName)
      option("exports", lib.jars.map { ":${fileToHttpRuleRepoName(it)}_import" })
      libVisibility?.let {
        visibility(arrayOf(it))
      }
    }

    for (jar in lib.jars) {
      val bazelLabel = fileToHttpRuleRepoName(jar)
      val label = "${bazelLabel}_import"
      if (!labelTracker.add(label)) {
        continue
      }

      val sourceJar = lib.sourceJars.singleOrNull { it.name == "${jar.nameWithoutExtension}-sources.jar" }
      if (isJavaLib) {
        load("@rules_java//java:defs.bzl", "java_import")
        target("java_import") {
          option("name", label)
          option("jars", arrayOf("@$bazelLabel//file"))
          if (sourceJar != null) {
            option("srcjar", "@${fileToHttpRuleFile(sourceJar)}")
          }
        }
      }
      else {
        target("kt_jvm_import") {
          option("name", label)
          option("jar", "@$bazelLabel//file")
          if (sourceJar != null) {
            option("srcjar", "@${fileToHttpRuleFile(sourceJar)}")
          }
        }
      }
    }
  }

  if (providedRequested.contains(lib)) {
    if (exportedCompilerPlugins.isEmpty()) {
      target("java_library") {
        option("name", targetName + PROVIDED_SUFFIX)
        option("exports", arrayOf(":$targetName"))
        option("neverlink", true)
        libVisibility?.let {
          visibility(arrayOf(it))
        }
      }
    }
    else {
      target("kt_jvm_library") {
        option("name", targetName + PROVIDED_SUFFIX)
        option("exports", arrayOf(":$targetName"))
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
  owner: LibOwnerDescriptor,
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

  val labelTracker = moduleFileToLabelTracker.computeIfAbsent(owner.moduleFile) { HashSet<String>() }
  buildFile(bazelFileUpdater, owner.sectionName) {
    for (lib in list) {
      for (jar in lib.jars) {
        val label = fileToHttpRuleRepoName(jar)
        if (!labelTracker.add(label)) {
          continue
        }

        val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = urlCache)
        target("http_file") {
          option("name", label)
          option("url", entry.url)
          option("sha256", entry.sha256)
          option("downloaded_file_path", jar.fileName.name)
        }
      }

      for (jar in lib.sourceJars) {
        val label = fileToHttpRuleRepoName(jar)
        if (!labelTracker.add(label)) {
          continue
        }

        val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = urlCache)
        target("http_file") {
          option("name", label)
          option("url", entry.url)
          option("sha256", entry.sha256)
          option("downloaded_file_path", jar.fileName.name)
        }
      }
    }
  }
}

private fun fileToHttpRuleRepoName(jar: Path): String = bazelLabelBadCharsPattern.replace(jar.nameWithoutExtension, "_") + "_http"

private fun fileToHttpRuleFile(jar: Path): String = fileToHttpRuleRepoName(jar) + "//file"

internal fun generateLocalLibs(libs: Set<LocalLibrary>, providedRequested: Set<LibOwner>, fileToUpdater: MutableMap<Path, BazelFileUpdater>) {
  for ((dir, libs) in libs.asSequence().sortedBy { it.lib.targetName }.groupBy { it.files.first().parent }) {
    val bazelFileUpdater = fileToUpdater.computeIfAbsent(dir.resolve("BUILD.bazel")) { BazelFileUpdater(it) }
    bazelFileUpdater.removeSections("local-libraries")
    buildFile(bazelFileUpdater, "local-libs") {
      load("@rules_java//java:defs.bzl", "java_import")
      for (lib in libs) {
        val targetName = lib.lib.targetName
        target("java_import") {
          option("name", targetName)
          option("jars", lib.files.map { it.fileName.toString() })
          option("visibility", arrayOf("//visibility:public"))
        }

        if (providedRequested.contains(lib)) {
          load("@rules_java//java:defs.bzl", "java_library")
          target("java_library") {
            option("name", targetName + PROVIDED_SUFFIX)
            option("exports", arrayOf(":$targetName"))
            option("neverlink", true)
            visibility(arrayOf("//visibility:public"))
          }
        }
      }
    }
  }
}
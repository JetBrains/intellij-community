// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

internal const val PROVIDED_SUFFIX = "-provided"

internal class Library(
  @JvmField val targetName: String,
  // excluded from equals / hashCode
  @JvmField val isCommunity: Boolean,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Library

    return targetName == other.targetName
  }

  override fun hashCode(): Int = 31 + targetName.hashCode()
}

internal interface LibOwner {
  val lib: Library
}

@Suppress("unused")
internal class MavenLibrary(
  @JvmField val mavenCoordinates: String,
  @JvmField val jars: List<Path>,
  @JvmField val sourceJars: List<Path>,
  @JvmField val javadocJars: List<Path>,
  override val lib: Library,
) : LibOwner {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MavenLibrary

    return lib == other.lib && mavenCoordinates == other.mavenCoordinates
  }

  override fun hashCode(): Int = 31 * lib.hashCode() + mavenCoordinates.hashCode()
}

internal class LocalLibrary(
  @JvmField val files: List<Path>,
  override val lib: Library,
) : LibOwner {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LocalLibrary

    if (files != other.files) return false
    if (lib != other.lib) return false

    return true
  }

  override fun hashCode(): Int = 31 * lib.hashCode() + files.hashCode()
}

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

internal fun generateMavenLibs(
  bazelFileUpdater: BazelFileUpdater,
  mavenLibraries: List<MavenLibrary>,
  providedRequested: Set<LibOwner>,
) {
  val labelChecker = HashSet<String>()
  buildFile(bazelFileUpdater, "maven-libs") {
    load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_import")

    for (lib in mavenLibraries.groupBy { it.lib.targetName }.flatMap { (_, values) -> listOf(values.maxBy { it.lib.targetName }) }) {
      generateMavenLib(lib = lib, labelChecker = labelChecker, providedRequested = providedRequested)
    }
  }
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

private fun BuildFile.generateMavenLib(
  lib: MavenLibrary,
  labelChecker: MutableSet<String>,
  providedRequested: Set<LibOwner>,
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
  val isJavaLib = !targetName.startsWith("jetbrains-annotations") && (targetName.startsWith("bigdatatools_") || targetName.startsWith("wire") || !isKotlinLib(lib.jars))

  var exportedCompilerPlugins = emptyList<String>()
  if (lib.jars.size == 1) {
    val jar = lib.jars.single()
    val libName = targetName
    if (!labelChecker.add(libName)) {
      return
    }

    val sourceJar = lib.sourceJars.singleOrNull { it.name == "${jar.nameWithoutExtension}-sources.jar" }

    if (isJavaLib) {
      target("java_import") {
        option("name", targetName)
        option("jars", arrayOf("@${escapeBazelLabel(jar.nameWithoutExtension)}_http//file"))
        if (sourceJar != null) {
          option("srcjar", "@${escapeBazelLabel(sourceJar.nameWithoutExtension)}_http//file")
        }
        visibility(arrayOf("//visibility:public"))
      }
    }
    else {
      target("kt_jvm_import") {
        option("name", targetName)
        option("jar", "@${escapeBazelLabel(jar.nameWithoutExtension)}_http//file")
        if (sourceJar != null) {
          option("srcjar", "@${escapeBazelLabel(sourceJar.nameWithoutExtension)}_http//file")
        }
        if (targetName == "kotlinx-serialization-core") {
          exportedCompilerPlugins = listOf("@lib//:kotlin-serialization-plugin")
          option("exported_compiler_plugins", exportedCompilerPlugins)
        }
        if (targetName == "rhizomedb-compiler-plugin") {
          //todo update to kotlin 2.1.0
          option("exported_compiler_plugins", arrayOf("@lib//:rhizomedb-plugin"))
        }

        visibility(arrayOf("//visibility:public"))
      }
    }
  }
  else {
    target("java_library") {
      option("name", targetName)
      option("exports", lib.jars.map { ":${escapeBazelLabel(it.nameWithoutExtension)}_import" })
      visibility(arrayOf("//visibility:public"))
    }

    for (jar in lib.jars) {
      val bazelLabel = escapeBazelLabel(jar.nameWithoutExtension)
      val label = "${bazelLabel}_import"
      if (!labelChecker.add(label)) {
        continue
      }

      val sourceJar = lib.sourceJars.singleOrNull { it.name == "${jar.nameWithoutExtension}-sources.jar" }
      if (isJavaLib) {
        target("java_import") {
          option("name", label)
          option("jars", arrayOf("@${bazelLabel}_http//file"))
          if (sourceJar != null) {
            option("srcjar", "@${escapeBazelLabel(sourceJar.nameWithoutExtension)}_http//file")
          }
        }
      }
      else {
        target("kt_jvm_import") {
          option("name", label)
          option("jar", "@${bazelLabel}_http//file")
          if (sourceJar != null) {
            option("srcjar", "@${escapeBazelLabel(sourceJar.nameWithoutExtension)}_http//file")
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
        visibility(arrayOf("//visibility:public"))
      }
    }
    else {
      target("kt_jvm_library") {
        option("name", targetName + PROVIDED_SUFFIX)
        option("exports", arrayOf(":$targetName"))
        option("neverlink", true)
        option("exported_compiler_plugins", exportedCompilerPlugins)
        visibility(arrayOf("//visibility:public"))
      }
    }
  }
}

@Suppress("DuplicatedCode")
internal fun generateProjectLibsBazelModule(file: Path, isCommunity: Boolean, jarRepositories: List<JarRepository>, m2Repo: Path, generator: BazelBuildFileGenerator) {
  val bazelFileUpdater = BazelFileUpdater(file)
  bazelFileUpdater.removeSections("maven-libraries")
  val labelTracker = hashSetOf<String>()
  buildFile(bazelFileUpdater, "maven-libs") {
    generator.libs.asSequence()
      .filterIsInstance<MavenLibrary>()
      .filter { it.lib.isCommunity == isCommunity }
      .sortedBy { it.lib.targetName }
      .flatMap { lib ->
        lib.jars.asSequence().map { jar ->
          val label = "${escapeBazelLabel(jar.nameWithoutExtension)}_http"
          if (labelTracker.contains(label)) {
            return@map
          }

          val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = generator.urlCache)
          labelTracker.add(label)
          target("http_file") {
            option("name", label)
            option("url", entry.url)
            option("sha256", entry.sha256)
            option("downloaded_file_path", jar.fileName.name)
          }
        } +
        lib.sourceJars.asSequence().map { jar ->
          val label = "${escapeBazelLabel(jar.nameWithoutExtension)}_http"
          if (labelTracker.contains(label)) {
            return@map
          }
          val entry = getUrlAndSha256(jar = jar, jarRepositories = jarRepositories, m2Repo = m2Repo, urlCache = generator.urlCache)
          labelTracker.add(label)
          target("http_file") {
            option("name", label)
            option("url", entry.url)
            option("sha256", entry.sha256)
            option("downloaded_file_path", jar.fileName.name)
          }
        }
      }
      .toList()
  }
  bazelFileUpdater.save()
}

internal fun generateLocalLibs(libs: Set<LocalLibrary>, providedRequested: Set<LibOwner>) {
  for ((dir, libs) in libs.asSequence().sortedBy { it.lib.targetName }.groupBy { it.files.first().parent }) {
    val bazelFileUpdater = BazelFileUpdater(dir.resolve("BUILD.bazel"))
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
          target("java_library") {
            option("name", targetName + PROVIDED_SUFFIX)
            option("exports", arrayOf(":$targetName"))
            option("neverlink", true)
            visibility(arrayOf("//visibility:public"))
          }
        }
      }
    }
    bazelFileUpdater.save()
  }
}
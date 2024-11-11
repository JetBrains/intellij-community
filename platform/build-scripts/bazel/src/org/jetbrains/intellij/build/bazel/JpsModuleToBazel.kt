// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.relativeTo

/**
 To enable debug logging in Bazel: --sandbox_debug --verbose_failures --define=kt_trace=1
 */
internal class JpsModuleToBazel {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val m2Repo = Path.of(System.getProperty("user.name"), ".m2/repository")
      val projectDir = Path.of(PathManager.getHomePath())
      val project = JpsSerializationManager.getInstance().loadProject(projectDir.toString(), mapOf("MAVEN_REPOSITORY" to m2Repo.toString()), true)
      val jarRepositories = loadJarRepositories(projectDir)

      val urlCache = UrlCache(cacheFile = projectDir.resolve("build/lib-lock.json"))

      val generator = BazelBuildFileGenerator(projectDir = projectDir, project = project, urlCache = urlCache)
      // first, generate community to collect libs, that used by community (to separate community and ultimate libs)
      val communityFiles = generator.generateModuleBuildFiles(isCommunity = true)
      val ultimateFiles = generator.generateModuleBuildFiles(isCommunity = false)
      generator.save(communityFiles)
      generator.save(ultimateFiles)

      deleteOldFiles(
        projectDir = projectDir,
        generatedFiles = (communityFiles.keys.asSequence() + ultimateFiles.keys.asSequence())
          .filter { it != projectDir }
          .sortedBy { projectDir.relativize(it).invariantSeparatorsPathString }
          .toList(),
      )

      val communityTargets = communityFiles.keys
        .asSequence()
        .map { projectDir.relativize(it).invariantSeparatorsPathString }
        .filter { it != "community/plugins/maven/remote-run" }
        .joinToString("\n") {
          val dir = it.removePrefix("community/").takeIf { it != "community" } ?: ""
          val ruleDir = "build/jvm-rules/"
          if (dir.startsWith(ruleDir)) {
            "@rules_jvm//${dir.removePrefix(ruleDir)}:all"
          }
          else {
            "@community//$dir:all"
          }
        }
      val ultimateTargets = ultimateFiles.keys.joinToString("\n") {
        "//" + projectDir.relativize(it).invariantSeparatorsPathString + ":all"
      } + "\n@community//plugins/maven/remote-run:all"
      Files.writeString(projectDir.resolve("build/bazel-community-targets.txt"), communityTargets)
      Files.writeString(projectDir.resolve("build/bazel-targets.txt"), communityTargets + "\n" + ultimateTargets)

      try {
        generateProjectLibraryBazelBuild(file = projectDir.resolve("community/build/lib/BUILD.bazel"), isCommunity = true, generator = generator)
        generateProjectLibraryBazelBuild(file = projectDir.resolve("build/lib/BUILD.bazel"), isCommunity = false, generator = generator)

        generateCommunityLibraryBazelModule(projectDir = projectDir, jarRepositories = jarRepositories, m2Repo = m2Repo, generator = generator)
        generateUltimateLibraryBazelModule(projectDir = projectDir, jarRepositories = jarRepositories, m2Repo = m2Repo, generator = generator)

        generateLocalLibraries(generator.libs.asSequence().filterIsInstance<LocalLibrary>().sortedBy { it.targetName })
      }
      finally {
        urlCache.save()
      }
    }
  }
}

private fun deleteOldFiles(projectDir: Path, generatedFiles: List<Path>) {
  val fileListFile = projectDir.resolve("build/bazel-generated-file-list.txt")
  val oldFiles = if (Files.exists(fileListFile)) Files.readAllLines(fileListFile).map { projectDir.resolve(it.trim()) } else emptySet()

  val filesToDelete = HashSet(oldFiles)
  filesToDelete.removeAll(generatedFiles)
  if (filesToDelete.isNotEmpty()) {
    println("Delete ${filesToDelete.size} old files")
    for (file in filesToDelete) {
      println("Delete old ${projectDir.relativize(file).invariantSeparatorsPathString}/BUILD.bazel")
      Files.deleteIfExists(file.resolve("BUILD.bazel"))
    }
  }

  Files.writeString(fileListFile, generatedFiles.joinToString("\n") { projectDir.relativize(it).invariantSeparatorsPathString })
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

private fun loadJarRepositories(projectDir: Path): List<JarRepository> {
  val jarRepositoriesXml = JDOMUtil.load(projectDir.resolve(".idea/jarRepositories.xml"))
  val component = jarRepositoriesXml.getChildren("component").single()
  return component.getChildren("remote-repository").map { element ->
    JarRepository(url = getOptionValue(element, "url"), isPrivate = getOptionValue(element, "id").contains("private"))
  }
}

private fun getOptionValue(element: Element, key: String): String {
  return element.getChildren("option").single { it.getAttributeValue("name") == key }.getAttributeValue("value")
}

private fun generateLocalLibraries(libs: Sequence<LocalLibrary>) {
  for ((dir, libs) in libs.groupBy { it.files.first().parent }) {
    val bazelFileUpdater = BazelFileUpdater(dir.resolve("BUILD.bazel"))
    bazelFileUpdater.removeSections("local-libraries")
    buildFile(bazelFileUpdater, "local-libs") {
      load("@rules_java//java:defs.bzl", "java_import")
      for (lib in libs) {
        target("java_import") {
          option("name", lib.targetName)
          option("jars", lib.files.map { it.fileName.toString() })
          option("visibility", arrayOf("//visibility:public"))
        }
      }
    }
    bazelFileUpdater.save()
  }
}

private fun generateProjectLibraryBazelBuild(file: Path, isCommunity: Boolean, generator: BazelBuildFileGenerator) {
  val bazelFileUpdater = BazelFileUpdater(file)
  bazelFileUpdater.removeSections("local-libraries")
  bazelFileUpdater.removeSections("maven-libraries")

  val mavenLibraries = generator.libs.filterIsInstance<MavenLibrary>().filter { it.isCommunity == isCommunity }.sortedBy { it.targetName }
  if (mavenLibraries.isNotEmpty()) {
    generateMavenLibs(bazelFileUpdater = bazelFileUpdater, mavenLibraries = mavenLibraries)
  }

  val localLibraries = generator.libs.filterIsInstance<LocalLibrary>().filter { it.isCommunity == isCommunity }.sortedBy { it.targetName }
  if (localLibraries.isNotEmpty()) {
    buildFile(bazelFileUpdater, "local-libs") {
      for (lib in localLibraries.groupBy { it.targetName }.flatMap { (_, values) -> listOf(values.maxBy { it.targetName }) }) {
        target("java_library") {
          option("name", lib.targetName)
          option("exports", arrayOf(lib.bazelLabel))
          if (lib.isProvided) {
            @Suppress("SpellCheckingInspection")
            option("neverlink", true)
          }
          visibility(arrayOf("//visibility:public"))
        }
      }
    }
  }
  bazelFileUpdater.save()
}

private fun generateMavenLibs(
  bazelFileUpdater: BazelFileUpdater,
  mavenLibraries: List<MavenLibrary>,
) {
  val labelChecker = HashSet<String>()
  buildFile(bazelFileUpdater, "maven-libs") {
    for (lib in mavenLibraries.groupBy { it.targetName }.flatMap { (_, values) -> listOf(values.maxBy { it.targetName }) }) {
      @Suppress("SpellCheckingInspection")
      if (lib.targetName == "bifurcan" || lib.targetName == "kotlinx-collections-immutable-jvm") {
        continue
      }

      if (lib.jars.size == 1) {
        val jar = lib.jars.single()
        val libName = lib.targetName
        if (!labelChecker.add(libName)) {
          continue
        }

        val sourcesJar = lib.sourceJars.singleOrNull { it.name == "${jar.nameWithoutExtension}-sources.jar" }
        target("kt_jvm_import") {
          option("name", lib.targetName)
          option("jar", "@${escapeBazelLabel(jar.nameWithoutExtension)}_http//file")
          if (sourcesJar != null) {
            option("srcjar", "@${escapeBazelLabel(sourcesJar.nameWithoutExtension)}_http//file")
          }
          if (lib.isProvided) {
            @Suppress("SpellCheckingInspection")
            option("neverlink", true)
          }
          visibility(arrayOf("//visibility:public"))
        }
      }
      else {
        target("java_library") {
          option("name", lib.targetName)
          option("exports", lib.jars.map { ":${escapeBazelLabel(it.nameWithoutExtension)}_import" })
          if (lib.isProvided) {
            @Suppress("SpellCheckingInspection")
            option("neverlink", true)
          }
          visibility(arrayOf("//visibility:public"))
        }

        for (jar in lib.jars) {
          val label = "${escapeBazelLabel(jar.nameWithoutExtension)}_import"
          if (!labelChecker.add(label)) {
            continue
          }

          val sourcesJar = lib.sourceJars.singleOrNull { it.name == "${jar.nameWithoutExtension}-sources.jar" }
          target("kt_jvm_import") {
            option("name", label)
            option("jar", "@${escapeBazelLabel(jar.nameWithoutExtension)}_http//file")
            if (sourcesJar != null) {
              option("srcjar", "@${escapeBazelLabel(sourcesJar.nameWithoutExtension)}_http//file")
            }
          }
        }
      }
    }
  }
}

private fun generateCommunityLibraryBazelModule(projectDir: Path, jarRepositories: List<JarRepository>, m2Repo: Path, generator: BazelBuildFileGenerator) {
  generateProjectLibraryBazelModule(
    file = projectDir.resolve("community/build/lib/MODULE.bazel"),
    isCommunity = true,
    jarRepositories = jarRepositories,
    m2Repo = m2Repo,
    generator = generator,
  )
}

private fun generateUltimateLibraryBazelModule(projectDir: Path, jarRepositories: List<JarRepository>, m2Repo: Path, generator: BazelBuildFileGenerator) {
  generateProjectLibraryBazelModule(
    file = projectDir.resolve("build/lib/MODULE.bazel"),
    isCommunity = false,
    jarRepositories = jarRepositories,
    m2Repo = m2Repo,
    generator = generator,
  )
}

@Suppress("DuplicatedCode")
private fun generateProjectLibraryBazelModule(file: Path, isCommunity: Boolean, jarRepositories: List<JarRepository>, m2Repo: Path, generator: BazelBuildFileGenerator) {
  val bazelFileUpdater = BazelFileUpdater(file)
  bazelFileUpdater.removeSections("maven-libraries")
  val labelTracker = hashSetOf<String>()
  buildFile(bazelFileUpdater, "maven-libs") {
    generator.libs.asSequence()
      .filterIsInstance<MavenLibrary>()
      .filter { it.isCommunity == isCommunity }
      .sortedBy { it.targetName }
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

internal data class ResourceDescriptor(
  @JvmField val baseDirectory: String,
  @JvmField val files: List<String>,
)

internal data class ModuleDescriptor(
  @JvmField val module: JpsModule,
  @JvmField val baseDirectory: Path,
  @JvmField val contentRoots: List<Path>,
  @JvmField val sources: List<String>,
  @JvmField val resources: List<ResourceDescriptor>,
  @JvmField val testSources: List<String>,
  @JvmField val testResources: List<ResourceDescriptor>,
  @JvmField val isCommunity: Boolean,
  @JvmField val bazelBuildFileDir: Path,
)
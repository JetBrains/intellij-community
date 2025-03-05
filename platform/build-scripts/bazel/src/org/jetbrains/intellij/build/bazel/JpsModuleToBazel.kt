// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.invariantSeparatorsPathString

/**
 To enable debug logging in Bazel: --sandbox_debug --verbose_failures --define=kt_trace=1
 */
internal class JpsModuleToBazel {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val m2Repo = Path.of(System.getProperty("user.home"), ".m2/repository")
      val projectDir = Path.of(PathManager.getHomePath())
      val project = JpsSerializationManager.getInstance().loadProject(projectDir.toString(), mapOf("MAVEN_REPOSITORY" to m2Repo.toString()), true)
      val jarRepositories = loadJarRepositories(projectDir)

      val urlCache = UrlCache(cacheFile = projectDir.resolve("build/lib-lock.json"))
      Runtime.getRuntime().addShutdownHook(thread(start = false, name = "Save URL cache") {
        println("Saving url cache to ${urlCache.cacheFile}")
        urlCache.save()
      })

      val generator = BazelBuildFileGenerator(projectDir = projectDir, project = project, urlCache = urlCache)
      val moduleList = generator.computeModuleList()
      // first, generate community to collect libs, that used by community (to separate community and ultimate libs)
      val communityFiles = generator.generateModuleBuildFiles(moduleList, isCommunity = true)
      val ultimateFiles = generator.generateModuleBuildFiles(moduleList, isCommunity = false)
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
        .sorted()
        .joinToString("\n") { path ->
          val dir = path.removePrefix("community/").takeIf { it != "community" } ?: ""
          val ruleDir = "build/jvm-rules/"
          if (dir.startsWith(ruleDir)) {
            "@rules_jvm//${dir.removePrefix(ruleDir)}:all"
          }
          else {
            "@community//$dir:all"
          }
        }
      val ultimateTargets = ultimateFiles.keys
        .sorted()
        .map { projectDir.relativize(it).invariantSeparatorsPathString }
        .joinToString("\n") {
          "//$it:all"
        }
      Files.writeString(projectDir.resolve("build/bazel-community-targets.txt"), communityTargets)
      Files.writeString(projectDir.resolve("build/bazel-targets.txt"), communityTargets + "\n" + ultimateTargets)

      try {
        generator.generateLibs(jarRepositories = jarRepositories, m2Repo = m2Repo)
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

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.moveTo

/**
 To enable debug logging in Bazel: --sandbox_debug --verbose_failures --define=kt_trace=1
 */
internal class JpsModuleToBazel {
  companion object {
    const val BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV = "BUILD_WORKSPACE_DIRECTORY"

    @JvmStatic
    fun main(args: Array<String>) {
      val workspaceDir: Path? = System.getenv(BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV)?.let { Path.of(it).normalize() }
      val projectDir = searchUltimateRootUpwards(workspaceDir ?: Path.of(System.getProperty("user.dir")))

      val m2Repo = Path.of(System.getProperty("user.home"), ".m2/repository")
      val project = JpsSerializationManager.getInstance().loadProject(projectDir.toString(), mapOf("MAVEN_REPOSITORY" to m2Repo.toString()), true)
      val jarRepositories = loadJarRepositories(projectDir)

      val urlCache = UrlCache(cacheFile = projectDir.resolve("build/lib-lock.json"))

      val generator = BazelBuildFileGenerator(projectDir = projectDir, project = project, urlCache = urlCache)
      val moduleList = generator.computeModuleList()
      // first, generate community to collect libs, that used by community (to separate community and ultimate libs)
      val communityResult = generator.generateModuleBuildFiles(moduleList, isCommunity = true)
      val ultimateResult = generator.generateModuleBuildFiles(moduleList, isCommunity = false)
      generator.save(communityResult.moduleBuildFiles)
      generator.save(ultimateResult.moduleBuildFiles)

      deleteOldFiles(
        projectDir = projectDir,
        generatedFiles = (communityResult.moduleBuildFiles.keys.asSequence() + ultimateResult.moduleBuildFiles.keys.asSequence())
          .filter { it != projectDir }
          .sortedBy { projectDir.relativize(it).invariantSeparatorsPathString }
          .toSet(),
      )

      generator.generateLibs(jarRepositories = jarRepositories, m2Repo = m2Repo)

      val targetsFile = projectDir.resolve("build/bazel-targets.json")
      saveTargets(targetsFile, communityResult.moduleTargets + ultimateResult.moduleTargets, moduleList.skippedModules)

      // save cache only on success. do not surround with try/finally
      urlCache.save()
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun saveTargets(file: Path, targets: List<BazelBuildFileGenerator.ModuleTargets>, skippedModules: Collection<String>) {
      @Serializable
      data class TargetsFileModuleDescription(
        val productionTargets: List<String>,
        val productionJars: List<String>,
        val testTargets: List<String>,
        val testJars: List<String>,
      )

      @Serializable
      data class TargetsFile(
        val modules: Map<String, TargetsFileModuleDescription>,
      )

      val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
      try {
        Files.writeString(
          tempFile, jsonSerializer.encodeToString(
          TargetsFile(
            modules = targets.associate { moduleTarget ->
              moduleTarget.moduleDescriptor.module.name to TargetsFileModuleDescription(
                productionTargets = moduleTarget.productionTargets,
                productionJars = moduleTarget.productionJars,
                testTargets = moduleTarget.testTargets,
                testJars = moduleTarget.testJars,
              )
            } + skippedModules.associateWith { moduleName -> TargetsFileModuleDescription(emptyList(), emptyList(), emptyList(), emptyList()) }
          )))
        tempFile.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } finally {
        tempFile.deleteIfExists()
      }
    }

    fun searchUltimateRootUpwards(start: Path): Path {
      var current = start
      while (true) {
        if (Files.exists(current.resolve(".ultimate.root.marker"))) {
          return current
        }

        current = current.parent ?: throw IllegalStateException("Cannot find ultimate root starting from $start")
      }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val jsonSerializer = Json {
      prettyPrint = true
      prettyPrintIndent = "  "
    }
  }
}

private fun deleteOldFiles(projectDir: Path, generatedFiles: Set<Path>) {
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

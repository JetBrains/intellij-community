// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.moveTo

/**
 To enable debug logging in Bazel: --sandbox_debug --verbose_failures --define=kt_trace=1
 */
internal class JpsModuleToBazel {
  companion object {
    const val BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV = "BUILD_WORKSPACE_DIRECTORY"
    const val RUN_WITHOUT_ULTIMATE_ROOT_ENV = "RUN_WITHOUT_ULTIMATE_ROOT"

    @JvmStatic
    fun main(args: Array<String>) {
      val workspaceDir: Path? = System.getenv(BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV)?.let { Path.of(it).normalize() }
      val runWithoutUltimateRoot = (System.getenv(RUN_WITHOUT_ULTIMATE_ROOT_ENV) ?: "false").toBooleanStrict()

      val communityRoot = searchCommunityRoot(workspaceDir ?: Path.of(System.getProperty("user.dir")))
      val ultimateRoot: Path? = if (!runWithoutUltimateRoot && communityRoot.parent.resolve(".ultimate.root.marker").exists()) {
        communityRoot.parent
      } else {
        null
      }

      println("Community root: $communityRoot")
      println("Ultimate root: $ultimateRoot")

      val projectDir = ultimateRoot ?: communityRoot

      val m2Repo = Path.of(System.getProperty("user.home"), ".m2/repository")
      val project = JpsSerializationManager.getInstance().loadProject(projectDir.toString(), mapOf("MAVEN_REPOSITORY" to m2Repo.toString()), true)
      val jarRepositories = loadJarRepositories(projectDir)

      val modulesBazel = listOfNotNull(
        ultimateRoot?.resolve("lib/MODULE.bazel"),
        communityRoot.resolve("lib/MODULE.bazel"),
      )

      val urlCache = UrlCache(modulesBazel, jarRepositories)

      val generator = BazelBuildFileGenerator(
        ultimateRoot = ultimateRoot,
        communityRoot = communityRoot,
        project = project,
        urlCache = urlCache,
      )
      val moduleList = generator.computeModuleList()
      // first, generate community to collect libs, that used by community (to separate community and ultimate libs)
      val communityResult = generator.generateModuleBuildFiles(moduleList, isCommunity = true)
      val ultimateResult = generator.generateModuleBuildFiles(moduleList, isCommunity = false)
      generator.save(communityResult.moduleBuildFiles)
      generator.save(ultimateResult.moduleBuildFiles)

      generator.generateLibs(jarRepositories = jarRepositories, m2Repo = m2Repo)

      // Check that after all workings of generator, all checksums from urls with checksums
      // are saved to MODULE.bazel correctly
      verifyHttpFileTargetsGeneration(urlCache, modulesBazel, jarRepositories)

      deleteOldFiles(
        projectDir = communityRoot,
        generatedFiles = communityResult.moduleBuildFiles.keys
          .filter { it != communityRoot }
          .sortedBy { communityRoot.relativize(it).invariantSeparatorsPathString }
          .toSet(),
      )

      if (ultimateRoot != null) {
        deleteOldFiles(
          projectDir = ultimateRoot,
          generatedFiles = ultimateResult.moduleBuildFiles.keys
            .filter { it != ultimateRoot }
            .sortedBy { ultimateRoot.relativize(it).invariantSeparatorsPathString }
            .toSet(),
        )
      }

      if (ultimateRoot != null) {
        val targetsFile = ultimateRoot.resolve("build/bazel-targets.json")
        saveTargets(targetsFile, communityResult.moduleTargets + ultimateResult.moduleTargets, moduleList)
      }
    }

    private fun verifyHttpFileTargetsGeneration(
      urlCache: UrlCache,
      modulesBazel: List<Path>,
      jarRepositories: List<JarRepository>,
    ) {
      val usedEntries = urlCache.getUsedEntries()
      val mapOnDisk = readModules(modulesBazel, jarRepositories, warningsAsErrors = true)

      if (mapOnDisk != usedEntries) {
        for (path in usedEntries.keys - mapOnDisk.keys) {
          error("Cannot find http_file for $path in $modulesBazel, but $path was used in maven libraries")
        }

        for (path in mapOnDisk.keys - usedEntries.keys) {
          error("There is an http_file for $path in $modulesBazel, but $path was not used in jps-to-bazel")
        }

        for (path in mapOnDisk.keys.intersect(usedEntries.keys)) {
          val onDisk = mapOnDisk[path]
          val usedEntry = usedEntries[path]
          if (onDisk != usedEntry) {
            error(
              "Different cache entries on disk ($modulesBazel) and what was used in jps-to-bazel." +
              "on disk $onDisk, used entry $usedEntry"
            )
          }
        }

        // SHOULD NOT BE REACHED
        error(
          "http_file entries on disk in $modulesBazel are different from maven libraries used in jps-to-bazel." +
          "Also, there is a bug in calculating difference between them."
        )
      }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun saveTargets(file: Path, targets: List<BazelBuildFileGenerator.ModuleTargets>, moduleList: ModuleList) {
      @Serializable
      data class TargetsFileModuleDescription(
        val productionTargets: List<String>,
        val productionJars: List<String>,
        val testTargets: List<String>,
        val testJars: List<String>,
        val exports: List<String>,
      )

      @Serializable
      data class TargetsFile(
        val modules: Map<String, TargetsFileModuleDescription>,
      )

      val skippedModules = moduleList.skippedModules

      val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
      try {
        Files.writeString(
          tempFile, jsonSerializer.encodeToString<TargetsFile>(
          serializer = jsonSerializer.serializersModule.serializer(),
          value = TargetsFile(
            modules = targets.associate { moduleTarget ->
              moduleTarget.moduleDescriptor.module.name to TargetsFileModuleDescription(
                productionTargets = moduleTarget.productionTargets,
                productionJars = moduleTarget.productionJars,
                testTargets = moduleTarget.testTargets,
                testJars = moduleTarget.testJars,
                exports = moduleList.deps[moduleTarget.moduleDescriptor]?.exports ?: emptyList(),
              )
            } + skippedModules.associateWith { moduleName -> TargetsFileModuleDescription(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()) }
          )))
        tempFile.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } finally {
        tempFile.deleteIfExists()
      }
    }

    fun searchCommunityRoot(start: Path): Path {
      var current = start
      while (true) {
        if (Files.exists(current.resolve("intellij.idea.community.main.iml"))) {
          return current
        }
        if (Files.exists(current.resolve("community/intellij.idea.community.main.iml"))) {
          return current.resolve("community")
        }

        current = current.parent ?: throw IllegalStateException("Cannot find community root starting from $start")
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

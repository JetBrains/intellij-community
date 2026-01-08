// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.TreeMap
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

/**
 To enable debug logging in Bazel: --sandbox_debug --verbose_failures --define=kt_trace=1
 */
internal class JpsModuleToBazel {
  companion object {
    const val BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV = "BUILD_WORKSPACE_DIRECTORY"
    const val RUN_WITHOUT_ULTIMATE_ROOT_ENV = "RUN_WITHOUT_ULTIMATE_ROOT"

    @JvmStatic
    fun main(args: Array<String>) {
      var workspaceDir = System.getenv(BAZEL_BUILD_WORKSPACE_DIRECTORY_ENV)
                         ?: System.getProperty("user.dir")
      var runWithoutUltimateRoot = System.getenv(RUN_WITHOUT_ULTIMATE_ROOT_ENV) ?: "false"
      var defaultCustomModules = "true"
      var bazelOutputBase: Path? = null
      var assertAllModuleOutputsExist = false
      var m2Repo = JpsMavenSettings.getMavenRepositoryPath()

      for (arg in args) {
        when {
          arg.startsWith("--run_without_ultimate_root=") ->
            runWithoutUltimateRoot = arg.substringAfter("=")
          arg.startsWith("--workspace_directory=") ->
            workspaceDir = arg.substringAfter("=")
          arg.startsWith("--default-custom-modules=") ->
            defaultCustomModules = arg.substringAfter("=")
          arg.startsWith("--assert-all-library-roots-exist-with-output-base=") -> {
            bazelOutputBase = Path.of(arg.substringAfter("="))
            check(bazelOutputBase.isAbsolute) { "Output base $bazelOutputBase must be absolute" }
            check(bazelOutputBase.normalize() == bazelOutputBase) { "Output base $bazelOutputBase must be normalized" }
            check(bazelOutputBase.exists()) { "Output base $bazelOutputBase must exist" }
          }
          arg == "--assert-all-module-outputs-exist" -> assertAllModuleOutputsExist = true
          arg.startsWith("--m2-repo=") ->
            m2Repo = arg.substringAfter("=")
          else -> error("Unknown argument: $arg")
        }
      }

      val communityRoot = searchCommunityRoot(Path.of(workspaceDir))
      val ultimateRoot: Path? = if (!runWithoutUltimateRoot.toBooleanStrict() && communityRoot.parent.resolve(".ultimate.root.marker").exists()) {
        communityRoot.parent
      } else {
        null
      }
      val bazelWorkspaceRoot = bazelOutputBase?.let {
        val workspaceLine = it.resolve("README").readLines().single { line -> line.startsWith("WORKSPACE: ") }
        Path.of(workspaceLine.removePrefixStrict("WORKSPACE: "))
      }

      println("Community root: $communityRoot")
      println("Ultimate root: $ultimateRoot")
      println("M2 repo root: $m2Repo")
      println("Bazel output base: $bazelOutputBase")

      val projectDir = ultimateRoot ?: communityRoot

      val project = JpsSerializationManager.getInstance().loadProject(
        /* projectPath = */ projectDir,
        /* externalConfigurationDirectory = */ null,
        /* pathVariables = */ mapOf("MAVEN_REPOSITORY" to m2Repo),
        /* loadUnloadedModules = */ true,
      )
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
        customModules = if (defaultCustomModules.toBooleanStrict()) DEFAULT_CUSTOM_MODULES else emptyMap(),
      )
      val moduleList = generator.computeModuleList(Path.of(m2Repo))
      // first, generate community to collect libs, that used by community (to separate community and ultimate libs)
      val communityResult = generator.generateModuleBuildFiles(moduleList, isCommunity = true)
      val ultimateResult = generator.generateModuleBuildFiles(moduleList, isCommunity = false)
      generator.save(communityResult.moduleBuildFiles)
      generator.save(ultimateResult.moduleBuildFiles)

      generator.generateLibs(jarRepositories = jarRepositories, m2Repo = Path.of(m2Repo))
      if (ultimateRoot != null && ultimateRoot.resolve("toolbox").exists()) {
        generator.generateToolboxDeps()
      }

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
        check(bazelWorkspaceRoot == null || bazelWorkspaceRoot == ultimateRoot) { "Bazel workspace ($bazelWorkspaceRoot) root must be ultimate root ($ultimateRoot)" }

        val ultimateTargetsFile = ultimateRoot.resolve("build/bazel-targets.json")
        saveTargets(
          file = ultimateTargetsFile,
          targets = communityResult.moduleTargets + ultimateResult.moduleTargets,
          moduleList = moduleList,
          libs = generator.allLibraries,
          communityRoot = communityRoot,
          ultimateRoot = ultimateRoot,
          projectRoot = ultimateRoot,
          assertAllModuleOutputsExist = assertAllModuleOutputsExist,
          bazelOutputBase = if (bazelWorkspaceRoot == ultimateRoot) bazelOutputBase else null,
        )

        saveDevServerRunConfigurations(ultimateRoot = ultimateRoot, targetFilePath = ultimateRoot.resolve("build").resolve("dev_server_run_configurations.bzl"))
      }
      else {
        check(bazelWorkspaceRoot == null || bazelWorkspaceRoot == communityRoot) { "Bazel workspace root ($bazelWorkspaceRoot) must be community root ($communityRoot)" }

        val communityTargetsFile = communityRoot.resolve("build/bazel-targets.json")
        saveTargets(
          file = communityTargetsFile,
          targets = communityResult.moduleTargets,
          moduleList = moduleList,
          libs = generator.communityOnlyLibraries,
          communityRoot = communityRoot,
          ultimateRoot = null,
          projectRoot = communityRoot,
          assertAllModuleOutputsExist = assertAllModuleOutputsExist,
          bazelOutputBase = if (bazelWorkspaceRoot == communityRoot) bazelOutputBase else null,
        )
      }
    }

    private fun verifyHttpFileTargetsGeneration(
      urlCache: UrlCache,
      modulesBazel: List<Path>,
      jarRepositories: List<JarRepository>,
    ) {
      val usedEntries = urlCache.getUsedEntries()

      if (usedEntries.isEmpty()) {
        check(modulesBazel.none { it.exists() }) {
          "No used entries -> not module bazel files generated: $modulesBazel should not exist"
        }
        return
      }

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
    fun saveTargets(
      file: Path,
      targets: List<BazelBuildFileGenerator.ModuleTargets>,
      moduleList: ModuleList,
      libs: Collection<Library>,
      communityRoot: Path,
      ultimateRoot: Path?,
      projectRoot: Path,
      assertAllModuleOutputsExist: Boolean,
      bazelOutputBase: Path?,
    ) {
      @Serializable
      data class LibraryDescription(
        val target: String,
        val jars: List<String>,
        val jarTargets: List<String>,
        val sourceJars: List<String>,
      )

      @Serializable
      data class TargetsFileModuleDescription(
        val productionTargets: List<String>,
        val productionJars: List<String>,
        val testTargets: List<String>,
        val testJars: List<String>,
        val exports: List<String>,
        val moduleLibraries: Map<String, LibraryDescription>,
      )

      @Serializable
      data class TargetsFile(
        val modules: Map<String, TargetsFileModuleDescription>,
        val projectLibraries: Map<String, LibraryDescription>,
      )

      fun makeJarPath(library: Library, file: MavenFileDescription): String {
        val path = "external/" +
                   library.target.container.repoLabel.removePrefix("@") +
                   "++_repo_rules+" +
                   "${fileToHttpRuleFile(file.mavenCoordinates)}/" +
                   "${file.mavenCoordinates.artifactId}-${file.mavenCoordinates.version}" +
                   (if (file.mavenCoordinates.classifier != null) "-${file.mavenCoordinates.classifier}" else "") +
                   file.mavenCoordinates.packaging

        if (bazelOutputBase != null) {
          check(bazelOutputBase.resolve(path).isRegularFile()) {
            "Cannot find ${bazelOutputBase.resolve(path)} (library ${library.target.jpsName} library module=${library.target.moduleLibraryModuleName})"
          }
        }

        return path
      }

      fun makeJarTarget(library: Library, file: MavenFileDescription): String {
        val target = library.target.container.repoLabel + "//:" +
                     mavenCoordinatesToFileName(file.mavenCoordinates, groupDirectory = true)

        return target
      }

      fun makeLibraryDescription(library: Library): LibraryDescription {
        val target = "${library.target.container.repoLabel}//:${library.target.targetName}"

        return when (library) {
          is MavenLibrary -> LibraryDescription(
            target = target,
            jars = library.jars.map { makeJarPath(library, it) },
            jarTargets = library.jars.map { makeJarTarget(library, it) },
            sourceJars = library.sourceJars.map { makeJarPath(library, it) },
          )

          is LocalLibrary -> {
            LibraryDescription(
              target = target,
              jars = library.files.map {
                val normalized = it.normalize()
                require(
                  normalized.startsWith(communityRoot) ||
                  (ultimateRoot != null && normalized.startsWith(ultimateRoot))
                ) {
                  "Library file $it is not under community root ($communityRoot) or ultimate root ($ultimateRoot)"
                }

                val ultimateLibRoot = ultimateRoot?.resolve("lib")
                val communityLibRoot = communityRoot.resolve("lib")

                val relativeToBazelOutputBase = when {
                  ultimateLibRoot != null && normalized.startsWith(ultimateLibRoot) ->
                    "external/ultimate_lib+/" + normalized.relativeTo(ultimateLibRoot).invariantSeparatorsPathString
                  normalized.startsWith(communityLibRoot) ->
                    "external/lib+/" + normalized.relativeTo(communityLibRoot).invariantSeparatorsPathString
                  projectRoot == ultimateRoot && normalized.startsWith(communityRoot) ->
                    "external/community+/" + normalized.relativeTo(communityRoot).invariantSeparatorsPathString
                  else -> "execroot/_main/${normalized.relativeTo(projectRoot).invariantSeparatorsPathString}"
                }

                if (bazelOutputBase != null) {
                  check(bazelOutputBase.resolve(relativeToBazelOutputBase).isRegularFile()) {
                    "Cannot find ${bazelOutputBase.resolve(relativeToBazelOutputBase)} (library ${library.target.jpsName} library module=${library.target.moduleLibraryModuleName})"
                  }
                }

                relativeToBazelOutputBase
              },
              jarTargets = library.files.map {
                val normalized = it.normalize()
                require(
                  normalized.startsWith(communityRoot) ||
                  (ultimateRoot != null && normalized.startsWith(ultimateRoot))
                ) {
                  "Library file $it is not under community root ($communityRoot) or ultimate root ($ultimateRoot)"
                }

                val ultimateLibRoot = ultimateRoot?.resolve("lib")
                val communityLibRoot = communityRoot.resolve("lib")

                fun Path.toBazelLabel(repoName: String, repoRoot: Path): String {
                  require(startsWith(repoRoot)) { "Path $this is not under root $repoRoot" }
                  require(normalize() == this) { "Path $this must be normalized" }
                  require(repoName.startsWith("@") || repoName.isEmpty()) { "Repo name $repoName must start with '@' or be empty" }
                  val relative = relativeTo(repoRoot)
                  return "$repoName//${if (relative.parent == null) "" else relative.parent.invariantSeparatorsPathString}:${relative.fileName}"
                }

                val target = when {
                  ultimateLibRoot != null && normalized.startsWith(ultimateLibRoot) ->
                    normalized.toBazelLabel("@ultimate_lib", ultimateLibRoot)
                  normalized.startsWith(communityLibRoot) ->
                    normalized.toBazelLabel("@lib", communityLibRoot)
                  projectRoot == ultimateRoot && normalized.startsWith(communityRoot) ->
                    normalized.toBazelLabel("@community", communityRoot)
                  else -> normalized.toBazelLabel("", projectRoot)
                }

                target
              },
              sourceJars = emptyList(),
            )
          }
        }
      }

      // When generating community-only file (ultimateRoot == null), strip the external/community+/ prefix
      // because community is the main workspace, not an external repository
      fun adjustJarPath(path: String): String {
        return if (ultimateRoot == null) {
          path.replace("external/community+/", "")
        } else {
          path
        }
      }

      val skippedModules = moduleList.skippedModules
      val emptyModule = TargetsFileModuleDescription(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
      val module2Libraries = libs
        .filter { it.target.moduleLibraryModuleName != null }
        .groupBy { it.target.moduleLibraryModuleName }

      val fileContent = jsonSerializer.encodeToString(
        serializer = jsonSerializer.serializersModule.serializer(),
        value = TargetsFile(
          modules = targets.associateTo(TreeMap()) { moduleTarget ->
            val moduleName = moduleTarget.moduleDescriptor.module.name
            moduleName to TargetsFileModuleDescription(
              productionTargets = moduleTarget.productionTargets.map { "$it.jar" },
              productionJars = moduleTarget.productionJars.map { adjustJarPath(it) },
              testTargets = moduleTarget.testTargets.map { "$it.jar" },
              testJars = moduleTarget.testJars.map { adjustJarPath(it) },
              exports = moduleList.deps[moduleTarget.moduleDescriptor]?.exports?.map { it.label } ?: emptyList(),
              moduleLibraries = module2Libraries[moduleName]
                ?.associateTo(TreeMap()) { it.target.jpsName to makeLibraryDescription(it) } ?: emptyMap(),
            ).also {
              if (assertAllModuleOutputsExist) {
                for (outputPath in it.productionJars + it.testJars) {
                  val absolutePath = projectRoot.resolve(outputPath)
                  check(absolutePath.exists()) { "Production target output does not exist: $absolutePath" }
                }
              }
            }
          } + skippedModules.associateWith { emptyModule },
          projectLibraries = libs.asSequence().mapNotNull {
            if (it.target.moduleLibraryModuleName != null) return@mapNotNull null
            return@mapNotNull it.target.jpsName to makeLibraryDescription(it)
          }.toMap(TreeMap())
        )
      )

      if (file.isRegularFile() && file.readText() == fileContent) {
        println("No changes in $file")
        return
      }

      println("Writing targets info to $file")
      val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
      try {
        tempFile.writeText(fileContent)
        tempFile.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } finally {
        tempFile.deleteIfExists()
      }
    }

    fun searchCommunityRoot(start: Path): Path {
      var current = start
      while (true) {
        if (Files.exists(current.resolve(".community.root.marker"))) {
          return current
        }
        if (Files.exists(current.resolve("community/.community.root.marker"))) {
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

  fileListFile.parent.createDirectories()
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

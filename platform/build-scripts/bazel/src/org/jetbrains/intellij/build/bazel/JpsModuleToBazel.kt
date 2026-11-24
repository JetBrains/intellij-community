// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SameParameterValue", "ReplaceGetOrSet", "KotlinPrintToLogpoint")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.relativeTo

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
      val skipGenerationOfPluginTargets = shouldSkipGenerationOfPluginTargets()
      if (skipGenerationOfPluginTargets) {
        println("Generation of plugin targets is disabled")
      }

      val projectDir = ultimateRoot ?: communityRoot
      val m2RepoPath = Path.of(m2Repo)

      val project = loadJpsProject(projectDir, communityRoot, m2Repo)
      val jarRepositories = loadJarRepositories(projectDir)

      val kotlincDefaults = parseKotlincProjectDefaults(communityRoot)
      generateCompilerOptionsBzl(communityRoot, kotlincDefaults)

      val modulesBazel = listOfNotNull(
        ultimateRoot?.resolve("lib/MODULE.bazel"),
        communityRoot.resolve("lib/MODULE.bazel"),
      )

      val urlCache = UrlCache(modulesBazel, jarRepositories)

      val generator = BazelBuildFileGenerator(
        ultimateRoot = ultimateRoot,
        communityRoot = communityRoot,
        project = project,
        projectDir = projectDir,
        urlCache = urlCache,
        customModules = if (defaultCustomModules.toBooleanStrict()) DEFAULT_CUSTOM_MODULES else emptyMap(),
        kotlincDefaults = kotlincDefaults,
      )
      val moduleList = generator.computeModuleList(m2RepoPath, skipGenerationOfPluginTargets)
      for ((moduleFile, root) in listOfNotNull(ultimateRoot?.let { it.resolve("MODULE.bazel") to it }, communityRoot.resolve("MODULE.bazel") to communityRoot)) {
        checkJpsModulesCall(
          moduleFile = moduleFile,
          projectRoot = root,
          modules = if (root == communityRoot) moduleList.allModules.filter { it.isCommunity } else moduleList.allModules,
          skippedModules = moduleList.skippedModules.toSet(),
          customModules = generator.customModules.keys,
          // Only the ultimate half reads `dev-build.json`, and every product needs the default build module.
          buildModules = if (root == communityRoot) emptyList() else listOf(DEFAULT_BUILD_MODULE),
          communityRoot = communityRoot,
          ultimateRoot = ultimateRoot,
        )
      }
      // Only ultimate has the hand input: a community checkout gets no `starter_test`.
      val starterTestTargets = ultimateRoot?.let { root ->
        readStarterBazelTests(root.resolve(STARTER_BAZEL_TESTS_RELATIVE_PATH))
          ?.resolveStarterTestTargets(JpsStarterTestModuleGraph(moduleList), source = STARTER_BAZEL_TESTS_RELATIVE_PATH)
      }
      val bazelFilesLoader = BazelFilesLoader()
      val manuallyWrittenAttributes = ManuallyWrittenAttributes(bazelFilesLoader)
      // first, generate community to collect libs that used by community (to separate community and ultimate libs)
      val communityResult = generator.generateModuleBuildFiles(moduleList, bazelFilesLoader, manuallyWrittenAttributes, isCommunity = true)
      val ultimateResult = generator.generateModuleBuildFiles(
        list = moduleList,
        bazelFilesLoader = bazelFilesLoader,
        manuallyWrittenAttributes = manuallyWrittenAttributes,
        isCommunity = false,
        starterTestTargets = starterTestTargets.orEmpty(),
      )
      generator.save(communityResult.moduleBuildFiles)
      generator.save(ultimateResult.moduleBuildFiles)
      if (ultimateRoot != null && starterTestTargets != null) {
        writeStarterBazelTestsGenerated(ultimateRoot, starterTestTargets.values)
      }

      generator.generateLibs(jarRepositories = jarRepositories, m2Repo = m2RepoPath)

      // Check that after all workings of generator, all checksums from urls with checksums
      // are saved to MODULE.bazel correctly
      verifyHttpFileTargetsGeneration(urlCache, modulesBazel, jarRepositories)

      deleteOldFiles(
        projectDir = communityRoot,
        generatedFiles = communityResult.generatedBuildFileDirs
          .filter { it != communityRoot }
          .sortedBy { communityRoot.relativize(it).invariantSeparatorsPathString }
          .toSet(),
        moduleNames = communityResult.moduleNamesByBuildFileDir,
        currentBuildFileDirs = communityResult.moduleBuildFiles.keys,
      )

      if (ultimateRoot != null) {
        deleteOldFiles(
          projectDir = ultimateRoot,
          generatedFiles = ultimateResult.generatedBuildFileDirs
            .filter { it != ultimateRoot }
            .sortedBy { ultimateRoot.relativize(it).invariantSeparatorsPathString }
            .toSet(),
          moduleNames = ultimateResult.moduleNamesByBuildFileDir,
          currentBuildFileDirs = ultimateResult.moduleBuildFiles.keys,
        )
      }

      if (ultimateRoot != null) {
        check(bazelWorkspaceRoot == null || bazelWorkspaceRoot == ultimateRoot) { "Bazel workspace ($bazelWorkspaceRoot) root must be ultimate root ($ultimateRoot)" }

        val moduleTargets = communityResult.moduleTargets + ultimateResult.moduleTargets
        val targetsFile = computeTargetsFile(
          targets = moduleTargets,
          moduleList = moduleList,
          libs = generator.allLibraries,
          communityRoot = communityRoot,
          ultimateRoot = ultimateRoot,
          projectRoot = ultimateRoot,
          assertAllModuleOutputsExist = assertAllModuleOutputsExist,
          bazelOutputBase = if (bazelWorkspaceRoot == ultimateRoot) bazelOutputBase else null,
        )
        saveTargets(file = ultimateRoot.resolve("build/bazel-targets.json"), targetsFile = targetsFile)
      }
      else {
        check(bazelWorkspaceRoot == null || bazelWorkspaceRoot == communityRoot) { "Bazel workspace root ($bazelWorkspaceRoot) must be community root ($communityRoot)" }

        val targetsFile = computeTargetsFile(
          targets = communityResult.moduleTargets,
          moduleList = moduleList,
          libs = generator.communityOnlyLibraries,
          communityRoot = communityRoot,
          ultimateRoot = null,
          projectRoot = communityRoot,
          assertAllModuleOutputsExist = assertAllModuleOutputsExist,
          bazelOutputBase = if (bazelWorkspaceRoot == communityRoot) bazelOutputBase else null,
        )
        saveTargets(file = communityRoot.resolve("build/bazel-targets.json"), targetsFile = targetsFile)
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
      val moduleLibraries: Map<String, LibraryDescription>,
      /**
       * The label of the descriptor of the module, see [moduleDescriptorLabel]. The dist generator copies it into
       * the descriptor rows that it writes. The JSON omits the key when the module has none.
       */
      val moduleDescriptor: String? = null,
    )

    /**
     * The `ij_plugin` target of one plugin, by its main module name.
     *
     * An entry exists only for a plugin whose `META-INF/plugin.xml` carries the `BUILD_USING_BAZEL_MARKER` comment.
     * The mirror of this class the platform reads it with is `BazelTargetsInfo.PluginDistributionTargetDescription`.
     */
    @Serializable
    data class PluginDistributionTargetDescription(
      @JvmField val target: String,
      /** The project-relative path of the `<target name>_dist` directory, which holds the plugin directory and `packed-modules.yaml`. */
      @JvmField val distributionDirectory: String,
    )

    @Serializable
    data class TargetsFile(
      val modules: Map<String, TargetsFileModuleDescription>,
      val imlTargets: List<String>,
      val projectLibraries: Map<String, LibraryDescription>,
      val pluginDistributionTargets: Map<String, PluginDistributionTargetDescription>,
    )

    fun saveTargets(file: Path, targetsFile: TargetsFile) {
      writeGeneratedFile(
        file = file,
        content = jsonSerializer.encodeToString(serializer = jsonSerializer.serializersModule.serializer(), value = targetsFile),
      )
    }

    fun computeTargetsFile(
      targets: List<BazelBuildFileGenerator.ModuleTargets>,
      moduleList: ModuleList,
      libs: Collection<Library>,
      communityRoot: Path,
      ultimateRoot: Path?,
      projectRoot: Path,
      assertAllModuleOutputsExist: Boolean,
      bazelOutputBase: Path?,
    ): TargetsFile {
      fun makeJarPath(library: Library, file: MavenFileDescription): String {
        val path = "external/" +
                   library.target.container.repoLabel.removePrefix("@") +
                   "++http_file+" +
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

      fun makeLibraryDescription(library: Library): LibraryDescription {
        // Not `repoLabel//:targetName`: that is the Maven form, and a local library's target is generated into the
        // package its files live in - `@lib//ant/lib:ant`, not `@lib//:ant` - so 41 of these used to be unresolvable.
        // This index is repo-global, so the one case with no single answer is a local library under the community root
        // but outside `community/lib`, which community writes as `//` and ultimate as `@community//`; the index is
        // written from whichever root the run has, and that is what `projectRoot` says.
        val target = libraryTargetLabel(
          library = library,
          communityRoot = communityRoot,
          ultimateRoot = ultimateRoot,
          isCommunityDependent = projectRoot != ultimateRoot,
        )
        val jarTargets = libraryJarTargets(
          library = library,
          communityRoot = communityRoot,
          ultimateRoot = ultimateRoot,
          projectRoot = projectRoot,
        )

        return when (library) {
          is MavenLibrary -> LibraryDescription(
            target = target,
            jars = library.jars.map { makeJarPath(library, it) },
            jarTargets = jarTargets,
            sourceJars = library.sourceJars.map { makeJarPath(library, it) },
          )

          is LocalLibrary -> LibraryDescription(
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
            jarTargets = jarTargets,
            sourceJars = emptyList(),
          )
        }
      }

      // When generating community-only file (ultimateRoot == null), strip the external/community+/ prefix
      // because community is the main workspace, not an external repository
      fun adjustOutputPath(path: String): String {
        return if (ultimateRoot == null) {
          path.replace("external/community+/", "")
        } else {
          path
        }
      }

      val skippedModules = moduleList.skippedModules
      val emptyModule = TargetsFileModuleDescription(emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
      val module2Libraries = libs
        .filter { it.target.moduleLibraryModuleName != null }
        .groupBy { it.target.moduleLibraryModuleName }

      val targetsFileValue = TargetsFile(
        modules = targets.associateTo(TreeMap()) { moduleTarget ->
          val moduleName = moduleTarget.moduleDescriptor.module.name
          moduleName to TargetsFileModuleDescription(
            productionTargets = moduleTarget.productionTargets.map { "$it.jar" },
            productionJars = moduleTarget.productionJars.map { adjustOutputPath(it) },
            testTargets = moduleTarget.testTargets.map { "$it.jar" },
            testJars = moduleTarget.testJars.map { adjustOutputPath(it) },
            moduleLibraries = module2Libraries[moduleName]
                                ?.associateTo(TreeMap()) { it.target.jpsName to makeLibraryDescription(it) } ?: emptyMap(),
            moduleDescriptor = moduleDescriptorLabel(module = moduleTarget.moduleDescriptor, communityRoot = communityRoot, ultimateRoot = ultimateRoot),
          ).also {
            if (assertAllModuleOutputsExist) {
              for (outputPath in it.productionJars + it.testJars) {
                val absolutePath = projectRoot.resolve(outputPath)
                check(absolutePath.exists()) { "Production target output does not exist: $absolutePath" }
              }
            }
          }
        } + skippedModules.associateWith { emptyModule },

        imlTargets = moduleList.allModules.asSequence()
          .map { imlTargetLabel(module = it, communityRoot = communityRoot, ultimateRoot = ultimateRoot) }
          .distinct()
          .sorted()
          .toList(),
        projectLibraries = libs.asSequence().distinctBy { it.target.jpsName }.mapNotNull {  // community project libraries are listed first, don't overwrite them with ultimate ones
          if (it.target.moduleLibraryModuleName != null) return@mapNotNull null
          return@mapNotNull it.target.jpsName to makeLibraryDescription(it)
        }.toMap(TreeMap()),
        pluginDistributionTargets =
          targets
            .asSequence()
            .mapNotNull { moduleTarget ->
              val pluginTarget = moduleTarget.pluginDistributionTarget ?: return@mapNotNull null
              moduleTarget.moduleDescriptor.module.name to PluginDistributionTargetDescription(
                target = pluginTarget.target,
                distributionDirectory = adjustOutputPath(pluginTarget.distributionDirectory),
              )
            }
            .sortedBy { it.first }
            .toMap(),
      )

      return targetsFileValue
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

private fun deleteOldFiles(projectDir: Path, generatedFiles: Set<Path>, moduleNames: Map<Path, List<String>>, currentBuildFileDirs: Set<Path>) {
  val fileListFile = projectDir.resolve(BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH)
  // `plugin-model-tool` owns the cross-half descriptor packages and deletes an absent one itself, so an old entry of that
  // directory is neither deleted nor carried into the new list.
  val oldFiles = if (Files.exists(fileListFile)) {
    Files.readAllLines(fileListFile)
      .map { it.trim().substringBefore(' ') }
      .filterNot { it.startsWith("$CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT/") }
      .map { projectDir.resolve(it) }
  }
  else {
    emptySet()
  }

  val filesToDelete = HashSet(oldFiles)
  filesToDelete.removeAll(generatedFiles)
  filesToDelete.removeAll(currentBuildFileDirs)
  if (filesToDelete.isNotEmpty()) {
    println("Delete ${filesToDelete.size} old files")
    for (file in filesToDelete) {
      println("Delete old ${projectDir.relativize(file).invariantSeparatorsPathString}/BUILD.bazel")
      Files.deleteIfExists(file.resolve("BUILD.bazel"))
    }
  }

  fileListFile.parent.createDirectories()
  Files.writeString(fileListFile, generatedFiles.joinToString("\n") { dir ->
    (listOf(projectDir.relativize(dir).invariantSeparatorsPathString) + moduleNames.get(dir).orEmpty().sorted()).joinToString(" ")
  })
}

/**
 * Which directories one repository half's last conversion generated, one project-relative directory per line, followed
 * by the modules whose records the conversion wrote there, separated by spaces. The `jps_modules` repository rule reads
 * the module names to tell a module without a record.
 */
internal const val BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH: String = "build/bazel-generated-file-list.txt"

/** Where `collectCrossHalfDescriptorPackages` of `distCrossHalfDescriptorPackage.kt` writes such a package. */
private const val CROSS_HALF_DESCRIPTOR_PACKAGE_ROOT: String = "build/dist-descriptors"

/**
 * The Bazel package a module's generated targets and exported files live in, as a label prefix.
 *
 * Top-level rather than local to [JpsModuleToBazel.Companion.computeTargetsFile], because [modulePackage] names the
 * descriptor files of a module in the same package, and the two must not compute it differently.
 */
internal fun bazelPackagePrefix(module: ModuleDescriptor, communityRoot: Path, ultimateRoot: Path?): String {
  fun makePackagePrefix(repoName: String, relativePath: String): String {
    return when {
      repoName.isEmpty() && relativePath.isEmpty() -> "//"
      repoName.isEmpty() -> "//$relativePath"
      relativePath.isEmpty() -> "$repoName//"
      else -> "$repoName//$relativePath"
    }
  }

  if (module.isCommunity) {
    val standaloneRepoRoot = when {
      module.bazelBuildFileDir.startsWith(communityRoot.resolve("platform/build-scripts/bazel")) -> communityRoot.resolve("platform/build-scripts/bazel") to "@jps_to_bazel"
      module.bazelBuildFileDir.startsWith(communityRoot.resolve("build/jvm-rules")) -> communityRoot.resolve("build/jvm-rules") to "@rules_jvm"
      else -> null
    }
    if (standaloneRepoRoot != null) {
      val (repoRoot, repoName) = standaloneRepoRoot
      return makePackagePrefix(repoName, module.bazelBuildFileDir.relativeTo(repoRoot).invariantSeparatorsPathString)
    }
  }

  val repoRoot = if (module.isCommunity) communityRoot else ultimateRoot ?: error("Ultimate root is not available")
  val repoName = if (module.isCommunity) "@community" else ""
  return makePackagePrefix(repoName, module.bazelBuildFileDir.relativeTo(repoRoot).invariantSeparatorsPathString)
}

/**
 * This option is temporarily added to allow switching generation of plugin targets if it leads to problems
 */
internal fun shouldSkipGenerationOfPluginTargets(): Boolean = System.getenv("SKIP_GENERATION_OF_PLUGIN_TARGETS").toBoolean()

internal fun loadJarRepositories(projectDir: Path): List<JarRepository> {
  val jarRepositoriesXml = JDOMUtil.load(projectDir.resolve(".idea/jarRepositories.xml"))
  val component = jarRepositoriesXml.getChildren("component").single()
  return component.getChildren("remote-repository").map { element ->
    JarRepository(url = getOptionValue(element, "url"), isPrivate = getOptionValue(element, "id").contains("private"))
  }
}

internal fun getOptionValue(element: Element, key: String): String {
  return element.getChildren("option").single { it.getAttributeValue("name") == key }.getAttributeValue("value")
}

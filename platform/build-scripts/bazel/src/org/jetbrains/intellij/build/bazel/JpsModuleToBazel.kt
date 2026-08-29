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
      var comparePluginContent = false
      var comparePluginCandidacy = false
      var writeDevDistResidue = false

      for (arg in args) {
        when {
          arg == "--compare-plugin-content" -> comparePluginContent = true
          arg == "--compare-plugin-candidacy" -> comparePluginCandidacy = true
          arg == "--write-dev-dist-residue" -> writeDevDistResidue = true
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
      val moduleList = generator.computeModuleList(m2RepoPath)
      checkPluginContentPopulation(moduleList = moduleList, context = generator)
      // first, generate community to collect libs that used by community (to separate community and ultimate libs)
      val communityResult = generator.generateModuleBuildFiles(moduleList, isCommunity = true, skipGenerationOfPluginTargets)
      val ultimateResult = generator.generateModuleBuildFiles(moduleList, isCommunity = false, skipGenerationOfPluginTargets)
      generator.save(communityResult.moduleBuildFiles)
      generator.save(ultimateResult.moduleBuildFiles)

      generator.generateLibs(jarRepositories = jarRepositories, m2Repo = m2RepoPath)
      generateDebuggerTestDepsModuleBazel(
        communityRoot = communityRoot,
        allLibraries = generator.allLibraries,
        urlCache = urlCache,
        m2Repo = m2RepoPath,
      )

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
        // The cross-half descriptor packages join the main repository's list. `plugin-model-tool` writes them and this
        // run does not, so they are named here rather than saved - see `crossHalfDescriptorPackageDirectories`.
        val crossHalfDescriptorPackages = crossHalfDescriptorPackageDirectories(
          ultimateRoot = ultimateRoot,
          population = generator.pluginDescriptorPopulation,
          moduleTargets = communityResult.moduleTargets + ultimateResult.moduleTargets,
        )
        deleteOldFiles(
          projectDir = ultimateRoot,
          generatedFiles = (ultimateResult.moduleBuildFiles.keys + crossHalfDescriptorPackages)
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

      // Last, and after generation has written everything: a measurement must not change what the run generates. The
      // switch lives inside this one binary because two separately built binaries cannot time each other - the wall
      // clock of one binary over one tree spanned 6.6 s to 205 s while a concurrent Bazel load came and went (ADR 0007
      // rule 6).
      if (comparePluginContent) {
        comparePluginContentProducers(moduleList = moduleList, context = generator, out = ::println)
      }
      if (comparePluginCandidacy) {
        comparePluginContentCandidacy(moduleList = moduleList, context = generator, out = ::println)
      }
      if (writeDevDistResidue) {
        val result = writeDevDistResidues(moduleList = moduleList, context = generator)
        println("dev-dist residue: written=${result.written} deleted=${result.deleted} unchanged=${result.unchanged}")
        for ((field, plugins) in result.pluginsPerField.entries.sortedByDescending { it.value }) {
          println("  $plugins plugins, ${result.rowsPerField.get(field)} rows  $field")
        }
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
      val exports: List<String>,
      val moduleLibraries: Map<String, LibraryDescription>,
      /**
       * The label of this module's `content_module_jar` target, or empty when it packs no `lib/` jar.
       *
       * Recorded rather than derived: it is not a function of any other label here - the target name is
       * `<bazel target name>_content_module_jar`, and the Bazel target name is itself derived from the JPS module name,
       * the package directory and the custom-module table. The plan generator names this label as a plugin's prepacked
       * content and as the platform payload's `packed`, so both sides have to mean the same target.
       */
      @JvmField val contentModuleJarTarget: String = "",
    )

    /**
     * What Bazel offers for one plugin, by its main module name.
     *
     * Every field is optional because the two halves are independent: `target`/`distributionDirectory` exist for a
     * plugin whose descriptor opted into `ij_plugin`, `contentTarget` for every plugin with a checked-in
     * `plugin-content.yaml`, and today those are almost disjoint sets. The mirror of this class the platform reads it
     * with is `BazelTargetsInfo.PluginDistributionTargetDescription`.
     */
    @Serializable
    data class PluginDistributionTargetDescription(
      @JvmField val target: String = "",
      @JvmField val distributionDirectory: String = "",
      @JvmField val contentTarget: String = "",
      /**
       * The label of this plugin's `dev_dist_plugin_descriptor` target, keyed by layout variant.
       *
       * A map and not one label, because a plugin whose descriptor differs by operating system or architecture declares
       * one target per layout variant and the empty key is the variant-less one. The dev-distribution plan resolves a
       * plugin entry to its descriptor target through this map, so a silently missing entry is a plugin whose patched
       * descriptor no fragment reads.
       */
      @JvmField val descriptorTargets: Map<String, String> = emptyMap(),
      /**
       * Prepack-eligible content modules of this plugin that its own `contentTarget` could not name.
       *
       * Module names, and only ever non-empty for a community plugin that packs ultimate modules: the community
       * repository cannot name an ultimate label, so the completion set in `//build/dev-dist-content` - the one package
       * that sees both repositories - is what turns these into `prepacked_content_modules`. Without this, every such
       * member silently fell back to `JarPackager`, which was 70 of the 79 relations the vetoes cost.
       */
      @JvmField val crossRepositoryPrepackedContentModules: List<String> = emptyList(),
    )

    @Serializable
    data class TargetsFile(
      val modules: Map<String, TargetsFileModuleDescription>,
      val imlTargets: List<String>,
      val projectLibraries: Map<String, LibraryDescription>,
      val pluginDistributionTargets: Map<String, PluginDistributionTargetDescription>,
      /**
       * The rows `dev_dist_plugin_content_candidate_overrides.txt` has to state, as
       * [communityOnlyCandidacyOverrideRows] derives them.
       *
       * Recorded rather than recomputed by the plan generator. The prepacked-candidate fold is repo-global and it reads
       * the project model, the residues and every member's own descriptor, and the plan generator cannot call the
       * derivation: the converter is the standalone Bazel module `jps_to_bazel`, built from published platform
       * artifacts, so its Kotlin is unreachable from the monorepo's targets. A second implementation of the fold on that
       * side is exactly the two-reader hazard this arc removes, so the one implementation states the answer here and the
       * plan generator writes it out.
       *
       * Empty from a community-only run, which is what such a run can honestly say: both arms of the delta are then the
       * same fold. The plan generator runs over the whole monorepo.
       *
       * The hermetic run states the same rows as the full-checkout run, because the delta reads the project model and
       * no other input. The plan generator is what keeps that true. It writes
       * `dev_dist_plugin_content_candidate_overrides.txt` from this field, and a validating run reads the hermetic file
       * and reports every difference against the checked-in one. A thinner field is then a failed validation with a
       * patch to apply, and not a quietly smaller distribution.
       *
       * Do not compare these rows against the checked-in file here. The plan generator needs this run to succeed
       * before it can correct that file, so such a check would block its own repair.
       */
      @JvmField val devDistPluginContentCandidateOverrides: List<String> = emptyList(),
    )

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
    ): TargetsFile {
      fun makeImlTarget(module: ModuleDescriptor): String {
        val relativeImlPath = module.imlFile.relativeTo(module.bazelBuildFileDir).invariantSeparatorsPathString
        return "${bazelPackagePrefix(module = module, communityRoot = communityRoot, ultimateRoot = ultimateRoot)}:$relativeImlPath"
      }

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
      val emptyModule = TargetsFileModuleDescription(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), "")
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
            exports = moduleList.deps[moduleTarget.moduleDescriptor]?.exports?.map { it.label } ?: emptyList(),
            moduleLibraries = module2Libraries[moduleName]
                                ?.associateTo(TreeMap()) { it.target.jpsName to makeLibraryDescription(it) } ?: emptyMap(),
            contentModuleJarTarget = moduleTarget.contentModuleJarTarget ?: "",
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
          .map { makeImlTarget(it) }
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
              val pluginTarget = moduleTarget.pluginDistributionTarget
              val contentTarget = moduleTarget.pluginContentTarget
              val crossRepositoryPrepacked = moduleTarget.crossRepositoryPrepackedModules
              val descriptorTargets = moduleTarget.pluginDescriptorTargets
              // Every half is independent of the others: a plugin can have no `ij_plugin` target and no content target
              // of its own and still have cross-repository members to complete, or a descriptor target alone.
              if (pluginTarget == null && contentTarget == null && crossRepositoryPrepacked.isEmpty() && descriptorTargets.isEmpty()) {
                return@mapNotNull null
              }

              moduleTarget.moduleDescriptor.module.name to PluginDistributionTargetDescription(
                target = pluginTarget?.target ?: "",
                distributionDirectory = pluginTarget?.let { adjustOutputPath(it.distributionDirectory) } ?: "",
                contentTarget = contentTarget ?: "",
                descriptorTargets = TreeMap(descriptorTargets),
                crossRepositoryPrepackedContentModules = crossRepositoryPrepacked,
              )
            }
            .sortedBy { it.first }
            .toMap(),
        devDistPluginContentCandidateOverrides = communityOnlyCandidacyOverrideRows(moduleList),
      )

      val fileContent = jsonSerializer.encodeToString(
        serializer = jsonSerializer.serializersModule.serializer(),
        value = targetsFileValue,
      )

      if (file.isRegularFile() && file.readText() == fileContent) {
        return targetsFileValue
      }

      file.parent.createDirectories()
      val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
      try {
        tempFile.writeText(fileContent)
        tempFile.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } finally {
        tempFile.deleteIfExists()
      }

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

private fun deleteOldFiles(projectDir: Path, generatedFiles: Set<Path>) {
  val fileListFile = projectDir.resolve(BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH)
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

/** Which directories one repository half's last conversion generated, one project-relative directory per line. */
internal const val BAZEL_GENERATED_FILE_LIST_RELATIVE_PATH: String = "build/bazel-generated-file-list.txt"

/**
 * The Bazel package a module's generated targets and exported files live in, as a label prefix.
 *
 * Top-level rather than local to [JpsModuleToBazel.Companion.saveTargets], because the parity check in
 * [JpsModuleToBazelTargetsOnly] has to name files in the same package and the two must not compute it differently.
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

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.walk

internal class JpsModuleToBazelTargetsOnly {
  companion object {
    private const val ULTIMATE_MARKER = ".ultimate.root.marker"
    private const val COMMUNITY_MARKER = ".community.root.marker"

    @OptIn(ExperimentalPathApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
      val expandedArgs = expandParamsFile(args)

      var manifest: Path? = null
      var defaultCustomModules = "true"
      var output: Path? = null

      for (arg in expandedArgs) {
        when {
          arg.startsWith("--manifest=") ->
            manifest = Path.of(arg.substringAfter("="))
          arg.startsWith("--default-custom-modules=") ->
            defaultCustomModules = arg.substringAfter("=")
          arg.startsWith("--output=") ->
            output = Path.of(arg.substringAfter("="))
          else -> error("Unknown argument: $arg")
        }
      }

      val outputFile = (requireNotNull(output) { "Missing required --output=<path> argument" })
        .absolute()
      check(manifest != null) { "Missing required --manifest=<path> argument" }

      val projectDir = Files.createTempDirectory("project")

      for (line in Files.readAllLines(manifest.toAbsolutePath())) {
        if (line.isBlank()) continue
        val split = line.split('\t', limit = 3)
        require(split.size == 3) { "Invalid manifest line (expected tab-separated action\\tsrc\\tdest): $line" }

        when (split[0]) {
          "copy" -> {
            val src = Path.of(split[1]).toAbsolutePath()
            val dest = projectDir.resolve(split[2])
            Files.createDirectories(dest.parent)
            src.copyTo(dest, overwrite = false)
          }
          "create" -> {
            val dest = projectDir.resolve(split[2])
            Files.createDirectories(dest.parent)
            Files.createFile(dest)
          }
          else -> error("Unknown action: ${split[0]}")
        }
      }

      var communityRoot: Path
      var ultimateRoot: Path?

      if (projectDir.resolve(ULTIMATE_MARKER).exists()) {
        ultimateRoot = projectDir
        communityRoot = projectDir.resolve("community")
        check(communityRoot.resolve(COMMUNITY_MARKER).exists()) {
          "Ultimate project should have both community/.community.root.marker and .ultimate.root.marker files"
        }
      }
      else if (projectDir.resolve(COMMUNITY_MARKER).exists()) {
        ultimateRoot = null
        communityRoot = projectDir
      }
      else {
        error("Project root should have either .community.root.marker or .ultimate.root.marker file")
      }

      val modulesXml = ModulesXml.readFromProject(projectDir)

      for (module in modulesXml.modules) {
        check(module.exists()) { "Module path $module does not exist" }
      }

      // use empty directory as m2 repo, targets-only run should not depend on maven resolver result
      val m2Repo = Files.createTempDirectory("m2-repo")

      try {

        val project = loadJpsProject(projectDir, communityRoot, m2Repo.absolutePathString())

        val generator = BazelBuildFileGenerator(
          ultimateRoot = ultimateRoot,
          communityRoot = communityRoot,
          project = project,
          projectDir = projectDir,
          // targets-only generation must depend only on the JPS project model
          urlCache = UrlCache(modulesBazel = emptyList(), repositories = emptyList()),
          customModules = if (defaultCustomModules.toBooleanStrict()) DEFAULT_CUSTOM_MODULES else emptyMap(),
          snapshotLibraryMode = SnapshotLibraryMode.REUSE_GENERATED,
          kotlincDefaults = parseKotlincProjectDefaults(projectDir),
        )

        // additional check: all iml files are available (to additionally check manifest)
        for (module in project.model.project.modules) {
          val imlDir = JpsModelSerializationDataService.getBaseDirectoryPath(module)!!
          val imlFile = imlDir.resolve("${module.name}.iml")
          if (!imlFile.exists()) {
            error("Missing file $imlFile")
          }
        }

        val skipGenerationOfPluginTargets = shouldSkipGenerationOfPluginTargets()
        val manuallyWrittenAttributes = ManuallyWrittenAttributes(BazelFilesLoader())
        val moduleList = generator.computeModuleList(m2Repo, skipGenerationOfPluginTargets)
        val communityTargets = generator.generateModuleTargets(moduleList, manuallyWrittenAttributes, isCommunity = true)
        val allTargets = if (ultimateRoot == null) {
          communityTargets
        }
        else {
          communityTargets + generator.generateModuleTargets(moduleList, manuallyWrittenAttributes, isCommunity = false)
        }

        val targetsFile = JpsModuleToBazel.computeTargetsFile(
          targets = allTargets,
          moduleList = moduleList,
          libs = if (ultimateRoot == null) generator.communityOnlyLibraries else generator.allLibraries,
          communityRoot = communityRoot,
          ultimateRoot = ultimateRoot,
          projectRoot = projectDir,
          assertAllModuleOutputsExist = false,
          bazelOutputBase = null,
        )
        JpsModuleToBazel.saveTargets(file = outputFile, targetsFile = targetsFile)

        val m2RepoFiles = m2Repo.walk().toList()
        if (m2RepoFiles.isNotEmpty()) {
          error("M2 repo should be empty, but contains $m2RepoFiles")
        }
      }
      finally {
        m2Repo.deleteRecursively()
      }
    }

    private fun expandParamsFile(args: Array<String>): List<String> {
      if (args.size == 1 && args[0].startsWith("@")) {
        val path = Path.of(args[0].substring(1))
        return Files.readAllLines(path)
          .filter { it.isNotBlank() }
          .map { line ->
            if (line.startsWith('\'')) {
              check(line.endsWith('\''))
              line.substring(1, line.length - 1)
            }
            else {
              line
            }
          }
      }
      return args.toList()
    }
  }
}

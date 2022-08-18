// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.BuildTasksImpl
import java.nio.file.Path

interface BuildTasks {
  companion object {
    @JvmStatic
    fun create(context: BuildContext): BuildTasks = BuildTasksImpl(context)
  }

  /**
   * Builds archive containing production source roots of the project modules. If `includeLibraries` is `true`, the produced
   * archive also includes sources of project-level libraries on which platform API modules from `modules` list depend on.
   */
  fun zipSourcesOfModules(modules: Collection<String>, targetFile: Path, includeLibraries: Boolean)

  fun zipSourcesOfModules(modules: Collection<String>, targetFile: Path) {
    zipSourcesOfModules(modules, targetFile, false)
  }

  /**
   * Produces distributions for all operating systems from sources. This includes compiling required modules, packing their output into JAR
   * files accordingly to [ProductProperties.productLayout], and creating distributions and installers for all OS.
   */
  fun buildDistributions()

  fun compileModulesFromProduct()

  /**
   * Compiles required modules and builds zip archives of the specified plugins in [artifacts][BuildPaths.artifactDir]/&lt;product-code&gt;-plugins
   * directory.
   */
  fun buildNonBundledPlugins(mainPluginModules: List<String>)

  /**
   * Generates a JSON file containing mapping between files in the product distribution and modules and libraries in the project configuration
   */
  fun generateProjectStructureMapping(targetFile: Path)

  fun compileProjectAndTests(includingTestsInModules: List<String>)

  fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>)

  fun compileModules(moduleNames: Collection<String>?) {
    compileModules(moduleNames, emptyList())
  }

  /**
   * Builds updater-full.jar artifact which includes 'intellij.platform.updater' module with all its dependencies
   */
  fun buildFullUpdaterJar()

  /**
   * Performs a fast dry run to check that the build scripts a properly configured. It'll run compilation, build platform and plugin JAR files,
   * build searchable options index and scramble the main JAR, but won't produce the product archives and installation files which occupy a lot
   * of disk space and take a long time to build.
   */
  fun runTestBuild()

  fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean)

  fun buildDmg(macZipDir: Path)
}

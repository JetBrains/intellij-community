// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
  suspend fun zipSourcesOfModules(modules: List<String>, targetFile: Path, includeLibraries: Boolean)

  fun zipSourcesOfModulesBlocking(modules: List<String>, targetFile: Path) {
    runBlocking {
      zipSourcesOfModules(modules, targetFile, includeLibraries = false)
    }
  }

  /**
   * Produces distributions for all operating systems from sources. This includes compiling required modules, packing their output into JAR
   * files accordingly to [ProductProperties.productLayout], and creating distributions and installers for all OS.
   */
  suspend fun buildDistributions()

  suspend fun compileModulesFromProduct()

  /**
   * Compiles required modules and builds zip archives of the specified plugins in [artifacts][BuildPaths.artifactDir]/&lt;product-code&gt;-plugins
   * directory.
   */
  suspend fun buildNonBundledPlugins(mainPluginModules: List<String>)

  @Deprecated("Use buildNonBundledPlugins", ReplaceWith("buildNonBundledPlugins(mainPluginModules)"))
  fun blockingBuildNonBundledPlugins(mainPluginModules: List<String>) {
    runBlocking(Dispatchers.Default) {
      buildNonBundledPlugins(mainPluginModules)
    }
  }

  fun compileProjectAndTests(includingTestsInModules: List<String>)

  fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>)

  fun compileModules(moduleNames: Collection<String>?) {
    compileModules(moduleNames = moduleNames, includingTestsInModules = java.util.List.of())
  }

  /**
   * Builds updater-full.jar artifact which includes 'intellij.platform.updater' module with all its dependencies
   */
  suspend fun buildFullUpdaterJar()

  suspend fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean)
}

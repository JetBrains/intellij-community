// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.BuildTasksImpl
import java.nio.file.Path

fun createBuildTasks(context: BuildContext): BuildTasks = BuildTasksImpl(context as BuildContextImpl)

interface BuildTasks {
  /**
   * Produces distributions for all operating systems from sources. This includes compiling required modules, packing their output into JAR
   * files accordingly to [ProductProperties.productLayout], and creating distributions and installers for all OS.
   */
  suspend fun buildDistributions()

  /**
   * Compiles required modules and builds zip archives of the specified plugins in [BuildContext.nonBundledPlugins]
   * directory.
   */
  suspend fun buildNonBundledPlugins(mainPluginModules: List<String>, dependencyModules: List<String> = emptyList())

  suspend fun buildUnpackedDistribution(targetDirectory: Path, includeBinAndRuntime: Boolean)
}

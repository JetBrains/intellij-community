// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.areCompiledClassesProvided
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sets up Kotlin compiler (downloaded from Marketplace) which is required for JPS to compile the repository
 */
internal class KotlinBinaries(private val communityHome: Path, private val options: BuildOptions, private val messages: BuildMessages) {
  val isCompilerRequired: Boolean
    get() = !areCompiledClassesProvided(options)

  val kotlinCompilerHome: Path by lazy {
    val compilerHome = KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(BuildDependenciesCommunityRoot(communityHome))
    val kotlinc = compilerHome.resolve("bin/kotlinc")
    check(Files.exists(kotlinc)) { "Kotlin compiler home is missing under the path: $compilerHome" }
    compilerHome
  }
}
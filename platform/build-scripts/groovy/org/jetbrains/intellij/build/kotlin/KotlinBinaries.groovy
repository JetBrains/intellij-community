// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.CompilationTasksImpl

import java.nio.file.Files
import java.nio.file.Path
/**
 * Sets up Kotlin compiler (downloaded from Marketplace) which is required for JPS to compile the repository
 */
@CompileStatic
final class KotlinBinaries {
  private final String communityHome
  private final BuildMessages messages
  private final BuildOptions options

  KotlinBinaries(String communityHome, BuildOptions options, BuildMessages messages) {
    this.options = options
    this.messages = messages
    this.communityHome = communityHome
  }

  @Lazy Path kotlinCompilerHome = {
    Path compilerHome = KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(new BuildDependenciesCommunityRoot(Path.of(communityHome)))
    def kotlinc = compilerHome.resolve("bin/kotlinc")
    if (!Files.exists(kotlinc)) {
      throw new IllegalStateException("Kotlin compiler home is missing under the path: " + compilerHome)
    }
    compilerHome
  }()

  boolean isCompilerRequired() {
    return !CompilationTasksImpl.areCompiledClassesProvided(options)
  }
}

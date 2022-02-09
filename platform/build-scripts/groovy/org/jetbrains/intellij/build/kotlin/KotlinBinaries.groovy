// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BuildUtils
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

  @Lazy Path kotlinJpsPluginJar = {
    KotlinCompilerDependencyDownloader.downloadKotlinJpsPlugin(new BuildDependenciesCommunityRoot(Path.of(communityHome)))
  }()

  @Lazy Path kotlinCompilerHome = {
    Path compilerHome = KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(new BuildDependenciesCommunityRoot(Path.of(communityHome)))
    def kotlinc = compilerHome.resolve("bin/kotlinc")
    if (!Files.exists(kotlinc)) {
      throw new IllegalStateException("Kotlin compiler home is missing under the path: " + compilerHome)
    }
    compilerHome
  }()

  /**
   * we need to add Kotlin JPS plugin to classpath before loading the project to ensure that Kotlin settings will be properly loaded
   */
  private void ensureKotlinJpsPluginIsAddedToClassPath(AntBuilder ant, Path kotlinJpsPluginJar, Path kotlinCompilerHome) {
    if (KotlinBinaries.class.getResource("/org/jetbrains/kotlin/jps/build/KotlinBuilder.class") != null) {
      return
    }

    def kotlincLibPath = "$kotlinCompilerHome/lib"
    def kotlinJpsPluginJarFile = kotlinJpsPluginJar.toFile()
    if (kotlinJpsPluginJarFile.exists() && new File(kotlincLibPath).exists()) {
      BuildUtils.addToJpsClassPath(kotlinJpsPluginJarFile.absolutePath, ant)
      ["kotlin-stdlib.jar"].each {
        def completePath = "$kotlincLibPath/$it"
        if (!new File(completePath).exists()) {
          throw new IllegalStateException("KotlinBinaries: '$completePath' doesn't exist")
        }
        BuildUtils.addToJpsClassPath(completePath, ant)
      }
    }
    else {
      messages.error("Could not find Kotlin JARs at $kotlinJpsPluginJar and $kotlinCompilerHome")
    }
  }

  boolean isCompilerRequired() {
    return !CompilationTasksImpl.areCompiledClassesProvided(options)
  }

  void setUpCompilerIfRequired(AntBuilder ant) {
    if (!options.skipDependencySetup) {
      def isCompilerRequired = isCompilerRequired()
      if (isCompilerRequired) {
        ensureKotlinJpsPluginIsAddedToClassPath(ant, kotlinJpsPluginJar, kotlinCompilerHome)
      }
    }
  }
}

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

  @Lazy Path kotlinPluginHome = {
    KotlinCompilerDependencyDownloader.downloadAndExtractKotlinPlugin(new BuildDependenciesCommunityRoot(Path.of(communityHome)))
  }()

  @Lazy Path kotlinCompilerHome = {
    Path compilerHome = kotlinPluginHome.resolve("Kotlin")
    if (!Files.isDirectory(compilerHome)) {
      throw new IllegalStateException("Kotlin compiler home in missing under Kotlin plugin: " + compilerHome)
    }
    compilerHome
  }()

  /**
   * we need to add Kotlin JPS plugin to classpath before loading the project to ensure that Kotlin settings will be properly loaded
   */
  private void ensureKotlinJpsPluginIsAddedToClassPath(AntBuilder ant, Path kotlinPlugin) {
    if (KotlinBinaries.class.getResource("/org/jetbrains/kotlin/jps/build/KotlinBuilder.class") != null) {
      return
    }

    Path compilerHome = kotlinPlugin.resolve("Kotlin")

    def kotlinPluginLibPath = "$compilerHome/lib"
    def kotlincLibPath = "$compilerHome/kotlinc/lib"
    if (new File(kotlinPluginLibPath).exists() && new File(kotlincLibPath).exists()) {
      ["jps/kotlin-jps-plugin.jar", "kotlin-plugin.jar", "kotlin-reflect.jar", "kotlin-common.jar"].each {
        def completePath = "$kotlinPluginLibPath/$it"
        if (!new File(completePath).exists()) {
          throw new IllegalStateException("KotlinBinaries: '$completePath' doesn't exist")
        }
        BuildUtils.addToJpsClassPath(completePath, ant)
      }
      ["kotlin-stdlib.jar"].each {
        def completePath = "$kotlincLibPath/$it"
        if (!new File(completePath).exists()) {
          throw new IllegalStateException("KotlinBinaries: '$completePath' doesn't exist")
        }
        BuildUtils.addToJpsClassPath(completePath, ant)
      }
    }
    else {
      messages.error("Could not find Kotlin JARs at $kotlinPluginLibPath and $kotlincLibPath")
    }
  }

  boolean isCompilerRequired() {
    return !CompilationTasksImpl.areCompiledClassesProvided(options)
  }

  void setUpCompilerIfRequired(AntBuilder ant) {
    if (!options.skipDependencySetup) {
      def isCompilerRequired = isCompilerRequired()
      if (isCompilerRequired) {
        ensureKotlinJpsPluginIsAddedToClassPath(ant, kotlinPluginHome)
      }
    }
  }
}

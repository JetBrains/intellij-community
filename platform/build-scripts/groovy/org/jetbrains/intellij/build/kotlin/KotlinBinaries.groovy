// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.GradleRunner
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.intellij.build.impl.CompilationContextImpl
import org.jetbrains.intellij.build.impl.CompilationTasksImpl

/**
 * Sets up Kotlin compiler (downloaded from Marketplace) which is required for JPS to compile the repository
 */
@CompileStatic
final class KotlinBinaries {
  static final String SET_UP_COMPILER_GRADLE_TASK = 'setupKotlinCompiler'
  private final String communityHome
  private final BuildMessages messages
  private final BuildOptions options
  final String compilerHome

  KotlinBinaries(String communityHome, BuildOptions options, BuildMessages messages) {
    this.options = options
    this.messages = messages
    this.communityHome = communityHome
    def compilerHome = "$communityHome/build/dependencies/build/kotlin-compiler/Kotlin"
    this.compilerHome = FileUtil.toSystemIndependentName(new File(compilerHome).canonicalPath)
  }

  /**
   * we need to add Kotlin JPS plugin to classpath before loading the project to ensure that Kotlin settings will be properly loaded
   */
  private void ensureKotlinJpsPluginIsAddedToClassPath(AntBuilder ant) {
    if (KotlinBinaries.class.getResource("/org/jetbrains/kotlin/jps/build/KotlinBuilder.class") != null) {
      return
    }

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
      messages.error(
        "Could not find Kotlin JARs at $kotlinPluginLibPath and $kotlincLibPath: run `./gradlew $SET_UP_COMPILER_GRADLE_TASK` in dependencies module to download Kotlin JARs"
      )
    }
  }

  boolean isCompilerRequired() {
    return !CompilationTasksImpl.areCompiledClassesProvided(options)
  }

  void setUpCompilerIfRequired(GradleRunner gradle, AntBuilder ant) {
    if (!options.skipDependencySetup) {
      def isCompilerRequired = isCompilerRequired()
      if (!options.isInDevelopmentMode) {
        CompilationContextImpl.setupCompilationDependencies(gradle, options, isCompilerRequired)
      }
      else if (isCompilerRequired) {
        gradle.run('Setting up Kotlin compiler', KotlinBinaries.SET_UP_COMPILER_GRADLE_TASK)
      }
      if (isCompilerRequired) {
        ensureKotlinJpsPluginIsAddedToClassPath(ant)
      }
    }
  }
}

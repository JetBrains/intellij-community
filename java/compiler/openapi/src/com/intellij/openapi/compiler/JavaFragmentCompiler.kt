// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration
import java.io.File
import java.io.IOException

/**
 * Compiles small Java sources (e.g. code fragments evaluated by the debugger) with a forked javac,
 * without a project build.
 *
 * The default implementation does not need the build system. The Java compiler plugin overrides it with the build settings of the project.
 */
@ApiStatus.Internal
interface JavaFragmentCompiler {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): JavaFragmentCompiler = project.service()
  }

  /**
   * The JDK that runs javac, with its version if known.
   *
   * @throws IllegalStateException if no compatible JDK is found
   */
  fun getJavacRuntimeSdk(): Pair<Sdk, JavaSdkVersion?>

  /**
   * The directory for temporary javac files, or `null` if the project has none.
   */
  fun getJavacCompilerWorkingDir(): File?

  /**
   * The annotation processing configuration of [module], or `null` to run javac without annotation processing.
   */
  fun getAnnotationProcessingConfiguration(module: Module): AnnotationProcessingConfiguration?

  /**
   * Compiles [files] into [outputDir] and returns the compiled classes.
   *
   * @throws CompilationException if javac reports errors
   */
  @Throws(IOException::class, CompilationException::class)
  fun compile(
    options: List<String>,
    platformCp: Collection<File>,
    classpath: Collection<File>,
    upgradeModulePath: Collection<File>,
    modulePath: Collection<File>,
    sourcePath: Collection<File>,
    files: Collection<File>,
    outputDir: File,
  ): Collection<ClassObject>
}

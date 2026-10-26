// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.ClassObject
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.JavaFragmentCompiler
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Pair
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration
import java.io.File

/**
 * [JavaFragmentCompiler] with the build settings of the project: the javac JDK of [BuildManager],
 * the annotation processing profiles of [CompilerConfiguration], and [CompilerManager] for compilation.
 */
internal class CompilerManagerJavaFragmentCompiler(private val project: Project) : JavaFragmentCompiler {
  override fun getJavacRuntimeSdk(): Pair<Sdk, JavaSdkVersion?> = BuildManager.getJavacRuntimeSdk(project)

  override fun getJavacCompilerWorkingDir(): File? = CompilerManager.getInstance(project).javacCompilerWorkingDir

  override fun getAnnotationProcessingConfiguration(module: Module): AnnotationProcessingConfiguration? {
    return CompilerConfiguration.getInstance(project).getAnnotationProcessingConfiguration(module)
  }

  override fun compile(
    options: List<String>,
    platformCp: Collection<File>,
    classpath: Collection<File>,
    upgradeModulePath: Collection<File>,
    modulePath: Collection<File>,
    sourcePath: Collection<File>,
    files: Collection<File>,
    outputDir: File,
  ): Collection<ClassObject> {
    return CompilerManager.getInstance(project).compileJavaCode(
      options, platformCp, classpath, upgradeModulePath, modulePath, sourcePath, files, outputDir
    )
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.BundledRuntime
import org.jetbrains.intellij.build.impl.CompilationTasksImpl
import org.jetbrains.intellij.build.impl.JpsCompilationData
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

interface CompilationContext {
  val options: BuildOptions
  val messages: BuildMessages
  val paths: BuildPaths
  val project: JpsProject
  val projectModel: JpsModel
  val dependenciesProperties: DependenciesProperties
  val bundledRuntime: BundledRuntime
  val compilationData: JpsCompilationData
  val portableCompilationCache: PortableCompilationCache

  fun isStepSkipped(step: String): Boolean = options.buildStepsToSkip.contains(step)

  /**
   * Stable java executable from Java SDK used to compile a project and do other stuff,
   * not a JBR to assert compatibility with a standard Java Runtime.
   */
  val stableJavaExecutable: Path

  /**
   * Stable JDK used to compile a project and run utilities, not a JBR to assert compatibility with a standard Java Runtime.
   */
  suspend fun getStableJdkHome(): Path

  /**
   * @return directory with compiled project classes, 'url' attribute value of 'output' tag from .idea/misc.xml by default
   */
  val classesOutputDirectory: Path

  fun findRequiredModule(name: String): JpsModule

  fun findModule(name: String): JpsModule?

  fun getModuleOutputDir(module: JpsModule): Path

  fun getModuleTestsOutputPath(module: JpsModule): String

  fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean = false): List<String>

  fun notifyArtifactBuilt(artifactPath: Path)
}

interface CompilationTasks {
  companion object {
    @JvmStatic
    fun create(context: CompilationContext): CompilationTasks = CompilationTasksImpl(context)
  }

  /**
   * See [compileModules]
   */
  fun compileAllModulesAndTests()

  /**
   * [resolveProjectDependencies] is guaranteed to be called
   */
  fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>? = emptyList())

  /**
   * [compileModules] is called if required
   */
  suspend fun buildProjectArtifacts(artifactNames: Set<String>)

  fun resolveProjectDependencies()
  
  fun generateRuntimeModuleRepository()
}


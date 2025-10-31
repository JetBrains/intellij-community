// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.BundledRuntime
import org.jetbrains.intellij.build.impl.CompilationTasksImpl
import org.jetbrains.intellij.build.impl.ModuleOutputProvider
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

interface CompilationContext : ModuleOutputProvider {
  val options: BuildOptions
  val messages: BuildMessages
  val paths: BuildPaths
  val project: JpsProject
  val projectModel: JpsModel
  val dependenciesProperties: DependenciesProperties
  val bundledRuntime: BundledRuntime
  val compilationData: JpsCompilationData

  fun isStepSkipped(step: String): Boolean = options.buildStepsToSkip.contains(step)

  /**
   * Stable java executable from Java SDK used to compile a project and do other stuff
   */
  val stableJavaExecutable: Path

  /**
   * Stable JDK used to compile a project and run utilities
   */
  suspend fun getStableJdkHome(): Path

  /**
   * @return directory with compiled project classes, 'url' attribute value of 'output' tag from .idea/misc.xml by default
   */
  val classesOutputDirectory: Path

  suspend fun getOriginalModuleRepository(): OriginalModuleRepository

  /**
   * A directory or a .jar file.
   */
  @Deprecated(message = "Please use `getModuleOutputRoots`", replaceWith = ReplaceWith("getModuleOutputRoots(module, forTests)"))
  fun getModuleOutputDir(module: JpsModule, forTests: Boolean = false): Path {
    val outputRoots = getModuleOutputRoots(module, forTests)
    return outputRoots.singleOrNull() ?: error("More than one output root for module '${module.name}': ${outputRoots.joinToString()}")
  }

  /**
   * A directory or a .jar file.
   */
  @Deprecated(message = "Please use `getModuleOutputRoots`", replaceWith = ReplaceWith("getModuleOutputRoots(module, forTests = true)"))
  suspend fun getModuleTestsOutputDir(module: JpsModule): Path {
    val outputRoots = getModuleOutputRoots(module, forTests = true)
    return outputRoots.singleOrNull() ?: error("More than one output root for module '${module.name}': ${outputRoots.joinToString()}")
  }

  suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean = false): List<String>

  fun findFileInModuleSources(moduleName: String, relativePath: String, forTests: Boolean = false): Path?

  fun findFileInModuleSources(module: JpsModule, relativePath: String, forTests: Boolean = false): Path?

  @Internal
  fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean = false): ByteArray?

  fun notifyArtifactBuilt(artifactPath: Path)

  @Internal
  fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext

  @Internal
  suspend fun prepareForBuild()

  suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>? = emptyList())

  @Internal
  suspend fun withCompilationLock(block: suspend () -> Unit)
}

interface CompilationTasks {
  companion object {
    fun create(context: CompilationContext): CompilationTasks = CompilationTasksImpl(context)
  }

  /**
   * See [compileModules]
   */
  suspend fun compileAllModulesAndTests()

  /**
   * [resolveProjectDependencies] is guaranteed to be called
   */
  suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>? = emptyList())

  suspend fun resolveProjectDependencies()
}

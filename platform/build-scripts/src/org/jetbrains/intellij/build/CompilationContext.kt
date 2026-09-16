// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.BundledRuntime
import org.jetbrains.intellij.build.impl.CompilationTasksImpl
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

interface CompilationContext {
  val httpSession: BuildHttpSession?
    get() = null

  val options: BuildOptions
  val messages: BuildMessages
  val paths: BuildPaths
  val project: JpsProject
  val projectModel: JpsModel
  val dependenciesProperties: DependenciesProperties
  val bundledRuntime: BundledRuntime
  val compilationData: JpsCompilationData

  val outputProvider: ModuleOutputProvider

  @Deprecated("use outputProvider", replaceWith = ReplaceWith("outputProvider.findRequiredModule(moduleName)"))
  fun findRequiredModule(moduleName: String): JpsModule = outputProvider.findRequiredModule(moduleName)

  fun isStepSkipped(step: String): Boolean = options.buildStepsToSkip.contains(step)

  /**
   * Stable java executable from Java SDK used to compile a project and do other stuff
   */
  val stableJavaExecutable: Path

  /**
   * Stable JDK used to compile a project and run utilities
   */
  fun getStableJdkHome(): Path

  /**
   * @return directory with compiled project classes, 'url' attribute value of 'output' tag from .idea/misc.xml by default
   */
  val classesOutputDirectory: Path

  fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean = false): Collection<Path>

  fun findFileInModuleSources(moduleName: String, relativePath: String, forTests: Boolean = false): Path?

  fun findFileInModuleSources(module: JpsModule, relativePath: String, forTests: Boolean = false): Path?

  fun notifyArtifactBuilt(artifactPath: Path)

  /** [lifetime] owns the caches of the copy; `null` keeps the lifetime of this context. */
  @Internal
  fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths, lifetime: BuildLifetime? = null): CompilationContext

  @Internal
  fun prepareForBuild()

  fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>? = emptyList())

  fun compileProductionModules() {
    compileModules(moduleNames = null, includingTestsInModules = emptyList())
  }

  @Internal
  fun withCompilationLock(block: () -> Unit)
}

interface CompilationTasks {
  companion object {
    fun create(context: CompilationContext): CompilationTasks = CompilationTasksImpl(context)
  }

  fun compileAllModulesAndTests()

  fun resolveProjectDependencies()
}

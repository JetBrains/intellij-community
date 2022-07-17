// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.BundledRuntime
import org.jetbrains.intellij.build.impl.CompilationTasksImpl
import org.jetbrains.intellij.build.impl.DependenciesProperties
import org.jetbrains.intellij.build.impl.JpsCompilationData
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

  fun isStepSkipped(step: String): Boolean = options.buildStepsToSkip.contains(step)

  /**
   * Stable java executable from Java SDK used to compile project and do other stuff,
   * not a JBR to assert compatibility with a standard Java Runtime
   */
  val stableJavaExecutable: Path

  /**
   * Stable JDK used to compile project and run utilities,
   * not a JBR to assert compatibility with a standard Java Runtime
   */
  val stableJdkHome: Path

  /**
   * @return directory with compiled project classes, url attribute value of output tag from .idea/misc.xml by default
   */
  val projectOutputDirectory: Path

  fun findRequiredModule(name: String): JpsModule

  fun findModule(name: String): JpsModule?

  /**
   * If module {@code newName} was renamed returns its old name and {@code null} otherwise. This method can be used to temporary keep names
   * of directories and JARs in the product distributions after renaming modules.
   */
  fun getOldModuleName(newName: String): String?

  fun getModuleOutputDir(module: JpsModule): Path

  fun getModuleTestsOutputPath(module: JpsModule): String

  fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String>

  // "Was" added due to Groovy bug (compilation error - cannot find method with same name but different parameter type)
  fun notifyArtifactWasBuilt(artifactPath: Path)
}

interface CompilationTasks {
  companion object {
    @JvmStatic
    fun create(context: CompilationContext): CompilationTasks = CompilationTasksImpl(context)
  }

  fun compileAllModulesAndTests()

  fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>? = emptyList())

  fun buildProjectArtifacts(artifactNames: Set<String>)

  fun resolveProjectDependencies()

  fun resolveProjectDependenciesAndCompileAll()

  fun reuseCompiledClassesIfProvided()
}


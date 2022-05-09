// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.api.trace.SpanBuilder
import org.jetbrains.intellij.build.impl.BuiltinModulesFileData
import org.jetbrains.intellij.build.impl.OsSpecificDistributionBuilder
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import java.util.function.UnaryOperator

abstract class BuildContext: CompilationContext {
  abstract val productProperties: ProductProperties
  abstract val windowsDistributionCustomizer: WindowsDistributionCustomizer
  abstract val linuxDistributionCustomizer: LinuxDistributionCustomizer
  abstract val macDistributionCustomizer: MacDistributionCustomizer
  abstract val proprietaryBuildTools: ProprietaryBuildTools

  abstract fun getApplicationInfo(): ApplicationInfoProperties

  /**
   * Build number without product code (e.g. '162.500.10')
   */
  abstract val buildNumber: String

  /**
   * Build number with product code (e.g. 'IC-162.500.10')
   */
  abstract val fullBuildNumber: String

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  abstract val systemSelector: String

  /**
   * Names of JARs inside `IDE_HOME/lib` directory which need to be added to the JVM boot classpath to start the IDE.
   */
  abstract val xBootClassPathJarNames: List<String>

  /**
   * Names of JARs inside `IDE_HOME/lib` directory which need to be added to the JVM classpath to start the IDE.
   */
  abstract var bootClassPathJarNames: MutableList<String>

  /**
   * Allows to customize classpath for buildSearchableOptions and builtinModules
   */
  abstract val classpathCustomizer: UnaryOperator<Set<String>>

  /**
   * Add file to be copied into application.
   */
  abstract fun addDistFile(file: Map.Entry<Path, String>)

  abstract fun getDistFiles(): Collection<Map.Entry<Path, String>>

  abstract fun includeBreakGenLibraries(): Boolean

  abstract fun patchInspectScript(path: Path)

  /**
   * Unlike VM options produced by {@link org.jetbrains.intellij.build.impl.VmOptionsGenerator},
   * these are hard-coded into launchers and aren't supposed to be changed by a user.
   */
  abstract fun getAdditionalJvmArguments(): List<String>

  abstract fun notifyArtifactBuilt(artifactPath: Path)

  abstract fun findApplicationInfoModule(): JpsModule

  abstract fun findFileInModuleSources(moduleName: String, relativePath: String): Path?

  @JvmOverloads
  fun signFile(file: Path, options: Map<String, String> = emptyMap()) {
    signFiles(listOf(file), options)
  }

  abstract fun signFiles(files: List<Path>, options: Map<String, String> = emptyMap())

  /**
   * Execute a build step or skip it if {@code stepId} is included into {@link BuildOptions#buildStepsToSkip}
   * @return {@code true} if the step was executed
   */
  abstract fun executeStep(stepMessage: String, stepId: String, step: Runnable): Boolean

  abstract fun executeStep(spanBuilder: SpanBuilder, stepId: String, step: Runnable)

  abstract fun shouldBuildDistributions(): Boolean

  abstract fun shouldBuildDistributionForOS(os: String): Boolean

  /**
   * Creates copy of this context which can be used to start a parallel task.
   * @param taskName short name of the task. It will be prepended to the messages from that task to distinguish them from messages from
   * other tasks running in parallel
   */
  abstract fun forkForParallelTask(taskName: String): BuildContext

  abstract fun createCopyForProduct(productProperties: ProductProperties, projectHomeForCustomizers: Path): BuildContext

  abstract fun getOsDistributionBuilder(os: OsFamily, ideaProperties: Path? = null): OsSpecificDistributionBuilder?

  /**
   * see BuildTasksImpl.buildProvidedModuleList
   */
  abstract fun getBuiltinModule(): BuiltinModulesFileData?
}

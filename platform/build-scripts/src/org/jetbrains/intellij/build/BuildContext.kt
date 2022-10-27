// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.diagnostic.telemetry.useWithScope2
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

interface BuildContext : CompilationContext {
  val productProperties: ProductProperties
  val windowsDistributionCustomizer: WindowsDistributionCustomizer?
  val linuxDistributionCustomizer: LinuxDistributionCustomizer?
  val proprietaryBuildTools: ProprietaryBuildTools

  val applicationInfo: ApplicationInfoProperties

  /**
   * Relative paths to files in distribution which should take 'executable' permissions.
   * No need to add *.sh.
   */
  fun addExtraExecutablePattern(os: OsFamily, pattern: String)

  fun getExtraExecutablePattern(os: OsFamily): List<String>

  /**
   * Build number without product code (e.g. '162.500.10')
   */
  val buildNumber: String

  /**
   * Build number with product code (e.g. 'IC-162.500.10')
   */
  val fullBuildNumber: String

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with an added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  val systemSelector: String

  /**
   * Names of JARs inside `IDE_HOME/lib` directory which need to be added to the JVM boot classpath to start the IDE.
   */
  val xBootClassPathJarNames: List<String>

  /**
   * Names of JARs inside `IDE_HOME/lib` directory which need to be added to the JVM classpath to start the IDE.
   */
  var bootClassPathJarNames: PersistentList<String>

  /**
   * see BuildTasksImpl.buildProvidedModuleList
   */
  var builtinModule: BuiltinModulesFileData?

  /**
   * Add file to be copied into application.
   */
  fun addDistFile(file: DistFile)

  fun getDistFiles(os: OsFamily?, arch: JvmArchitecture?): Collection<DistFile>

  fun includeBreakGenLibraries(): Boolean

  fun patchInspectScript(path: Path)

  /**
   * Unlike VM options produced by {@link org.jetbrains.intellij.build.impl.VmOptionsGenerator},
   * these are hard-coded into launchers and aren't supposed to be changed by a user.
   */
  fun getAdditionalJvmArguments(os: OsFamily, arch: JvmArchitecture, isScript: Boolean = false, isPortableDist: Boolean = false): List<String>

  fun notifyArtifactBuilt(artifactPath: Path)

  fun findApplicationInfoModule(): JpsModule

  fun findFileInModuleSources(moduleName: String, relativePath: String): Path?

  suspend fun signFiles(files: List<Path>, options: PersistentMap<String, String> = persistentMapOf()) {
    proprietaryBuildTools.signTool.signFiles(files = files, context = this, options = options)
  }

  /**
   * Execute a build step or skip it if {@code stepId} is included in {@link BuildOptions#buildStepsToSkip}
   * @return {@code true} if the step was executed
   */
  fun executeStep(stepMessage: String, stepId: String, step: Runnable): Boolean

  fun shouldBuildDistributions(): Boolean

  fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean

  fun createCopyForProduct(productProperties: ProductProperties, projectHomeForCustomizers: Path): BuildContext
}

suspend inline fun BuildContext.executeStep(spanBuilder: SpanBuilder, stepId: String, crossinline step: suspend () -> Unit) {
  if (isStepSkipped(stepId)) {
    spanBuilder.startSpan().addEvent("skip").end()
  }
  else {
    spanBuilder.useWithScope2 { step() }
  }
}

data class BuiltinModulesFileData(
  val bundledPlugins: List<String>,
  val modules: List<String>,
  val fileExtensions: List<String>,
)

data class DistFile(@JvmField val file: Path,
                    @JvmField val relativePath: String,
                    @JvmField val os: OsFamily? = null,
                    @JvmField val arch: JvmArchitecture? = null)

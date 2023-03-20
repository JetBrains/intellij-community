// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope2
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

interface BuildContext : CompilationContext {
  val productProperties: ProductProperties
  val windowsDistributionCustomizer: WindowsDistributionCustomizer?
  val linuxDistributionCustomizer: LinuxDistributionCustomizer?
  val proprietaryBuildTools: ProprietaryBuildTools

  val applicationInfo: ApplicationInfoProperties

  val isMacCodeSignEnabled: Boolean
    get() = !isStepSkipped(BuildOptions.MAC_SIGN_STEP) && proprietaryBuildTools.signTool.signNativeFileMode != SignNativeFileMode.DISABLED

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

  /**
   * @return sorted [DistFile] collection
   */
  fun getDistFiles(os: OsFamily?, arch: JvmArchitecture?): Collection<DistFile>

  fun includeBreakGenLibraries(): Boolean

  fun patchInspectScript(path: Path)

  /**
   * Unlike VM options produced by {@link org.jetbrains.intellij.build.impl.VmOptionsGenerator},
   * these are hard-coded into launchers and aren't supposed to be changed by a user.
   */
  fun getAdditionalJvmArguments(os: OsFamily,
                                arch: JvmArchitecture,
                                isScript: Boolean = false,
                                isPortableDist: Boolean = false): List<String>

  fun findApplicationInfoModule(): JpsModule

  fun findFileInModuleSources(moduleName: String, relativePath: String): Path?

  fun findFileInModuleSources(module: JpsModule, relativePath: String): Path?

  suspend fun signFiles(files: List<Path>, options: PersistentMap<String, String> = persistentMapOf()) {
    proprietaryBuildTools.signTool.signFiles(files = files, context = this, options = options)
  }

  fun shouldBuildDistributions(): Boolean

  fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean

  fun createCopyForProduct(productProperties: ProductProperties, projectHomeForCustomizers: Path): BuildContext
}

@Obsolete
fun executeStepSync(context: BuildContext, stepMessage: String, stepId: String, step: Runnable): Boolean {
  if (context.isStepSkipped(stepId)) {
    Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("name"), stepMessage))
  }
  else {
    spanBuilder(stepMessage).use {
      step.run()
    }
  }
  return true
}

suspend inline fun BuildContext.executeStep(spanBuilder: SpanBuilder, stepId: String, crossinline step: suspend (Span) -> Unit) {
  if (isStepSkipped(stepId)) {
    spanBuilder.startSpan().addEvent("skip '$stepId' step").end()
  }
  else {
    spanBuilder.useWithScope2(step)
  }
}

@Serializable
class BuiltinModulesFileData(
  @JvmField val plugins: List<String>,
  @JvmField val modules: List<String>,
  @JvmField val fileExtensions: List<String>,
)

data class DistFile(@JvmField val file: Path,
                    @JvmField val relativePath: String,
                    @JvmField val os: OsFamily? = null,
                    @JvmField val arch: JvmArchitecture? = null)

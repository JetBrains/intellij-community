// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.buildData.productInfo.ProductInfoLayoutItem
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.plugins.PluginAutoPublishList
import org.jetbrains.intellij.build.io.DEFAULT_TIMEOUT
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

interface BuildContext : CompilationContext {
  val productProperties: ProductProperties
  val windowsDistributionCustomizer: WindowsDistributionCustomizer?
  val macDistributionCustomizer: MacDistributionCustomizer?
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
   * IDE build number without product code (e.g. '251.500.10').
   * [org.jetbrains.intellij.build.impl.SnapshotBuildNumber.VALUE] by default.
   * May not match [com.intellij.util.text.SemVer].
   */
  val buildNumber: String

  /**
   * Build number used for all plugins being built.
   *
   * The value is [buildNumber] having:
   * * `SNAPSHOT` suffix replaced with `${DATE}` to match [com.intellij.util.text.SemVer];
   * * `.0` appended if [BuildContext.isNightlyBuild] to match [com.intellij.util.text.SemVer].
   *
   * See also [org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec.withCustomVersion].
   */
  val pluginBuildNumber: String

  /**
   * Build number with product code (e.g. 'IC-251.500.10')
   */
  val fullBuildNumber: String

  /**
   * An identifier which will be used to form names for directories where configuration and caches will be stored, usually a product name
   * without spaces with an added version ('IntelliJIdea2016.1' for IntelliJ IDEA 2016.1)
   */
  val systemSelector: String

  /**
   * Names of JARs inside `IDE_HOME/lib` directory which need to be added to the JVM classpath to start the IDE.
   *
   * **Note**: In terms of JVM, these JARs form a regular classpath (`-cp`), not a boot classpath (`-Xbootclasspath`).
   */
  var bootClassPathJarNames: List<String>

  /**
   * Specifies name of Java class which should be used to start the IDE.
   */
  val ideMainClassName: String

  /**
   * Specifies whether the new modular loader should be used in the IDE distributions, see [ProductProperties.rootModuleForModularLoader] and
   * [BuildOptions.useModularLoader].
   */
  val useModularLoader: Boolean

  /**
   * Specifies whether the runtime module repository should be added to the distributions, see [BuildOptions.generateRuntimeModuleRepository].
   */
  val generateRuntimeModuleRepository: Boolean

  /**
   * Returns main modules' names of plugins bundled with the product.
   * In IDEs, which use path-based loader, this list is specified manually in [ProductModulesLayout.bundledPluginModules] property.
   */
  suspend fun getBundledPluginModules(): List<String>

  /**
   * See [BuildOptions.PROVIDED_MODULES_LIST_STEP]
   */
  var builtinModule: BuiltinModulesFileData?

  val appInfoXml: String

  /**
   * [BuildPaths.artifactDir]/[ApplicationInfoProperties.productCode]-plugins/
   */
  val nonBundledPlugins: Path

  /**
   * [nonBundledPlugins]/auto-uploading/
   *
   * See [ProductModulesLayout.buildAllCompatiblePlugins]
   */
  val nonBundledPluginsToBePublished: Path

  /**
   * Add the file to be copied into an application.
   */
  fun addDistFile(file: DistFile)

  /**
   * @return sorted [DistFile] collection
   */
  fun getDistFiles(os: OsFamily?, arch: JvmArchitecture?, libcImpl: LibcImpl?): Collection<DistFile>

  fun patchInspectScript(path: Path)

  /**
   * Unlike VM options produced by [org.jetbrains.intellij.build.impl.VmOptionsGenerator],
   * these are hard-coded into launchers and aren't supposed to be changed by a user.
   */
  fun getAdditionalJvmArguments(
    os: OsFamily,
    arch: JvmArchitecture,
    isScript: Boolean = false,
    isPortableDist: Boolean = false,
    isQodana: Boolean = false,
  ): List<String>

  fun findApplicationInfoModule(): JpsModule

  suspend fun signFiles(files: List<Path>, options: PersistentMap<String, String> = persistentMapOf()) {
    proprietaryBuildTools.signTool.signFiles(files, context = this, options)
  }

  suspend fun getFrontendModuleFilter(): FrontendModuleFilter
  
  suspend fun getContentModuleFilter(): ContentModuleFilter

  val isEmbeddedFrontendEnabled: Boolean

  fun shouldBuildDistributions(): Boolean

  fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean

  suspend fun createCopyForProduct(
    productProperties: ProductProperties,
    projectHomeForCustomizers: Path,
    prepareForBuild: Boolean = true,
  ): BuildContext

  fun reportDistributionBuildNumber()

  suspend fun cleanupJarCache()

  /**
   * Creates an instance of [IntellijProductRunner] which can be used to run the IDE being built with some command.
   * @param additionalPluginModules main modules of non-bundled plugins, which should be loaded inside the IDE process
   * @param forceUseDevBuild if `true`, the 'dev build' approach will be used to run the IDE even if it uses the module-based loader
   * which supports running the IDE without running the build scripts.
   */
  suspend fun createProductRunner(additionalPluginModules: List<String> = emptyList(), forceUseDevBuild: Boolean = false): IntellijProductRunner

  suspend fun runProcess(
    args: List<String>,
    workingDir: Path? = null,
    timeout: Duration = DEFAULT_TIMEOUT,
    additionalEnvVariables: Map<String, String> = emptyMap(),
    attachStdOutToException: Boolean = false,
  )

  val pluginAutoPublishList: PluginAutoPublishList

  val isNightlyBuild: Boolean

  @ApiStatus.Internal
  suspend fun distributionState(): DistributionBuilderState
}

suspend inline fun <T> BuildContext.executeStep(
  spanBuilder: SpanBuilder,
  stepId: String,
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  crossinline step: suspend CoroutineScope.(Span) -> T,
): T? {
  return spanBuilder.use(coroutineContext) { span ->
    try {
      options.buildStepListener.onStart(stepId, messages)
      if (isStepSkipped(stepId)) {
        span.addEvent("skip '$stepId' step")
        options.buildStepListener.onSkipping(stepId, messages)
        null
      }
      else {
        step(span)
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      span.recordException(e)
      options.buildStepListener.onFailure(stepId, e, messages)
      null
    }
    finally {
      options.buildStepListener.onCompletion(stepId, messages)
    }
  }
}

@Serializable
class BuiltinModulesFileData(
  @JvmField val plugins: MutableList<String> = mutableListOf(),
  @JvmField var layout: List<ProductInfoLayoutItem> = emptyList(),
  @JvmField val fileExtensions: MutableList<String> = mutableListOf(),
)

sealed interface DistFileContent {
  fun readAsStringForDebug(): String
}

data class LocalDistFileContent(@JvmField val file: Path, val isExecutable: Boolean = false) : DistFileContent {
  override fun readAsStringForDebug(): String = Files.newInputStream(file).readNBytes(1024).decodeToString()

  override fun toString(): String = "LocalDistFileContent(file=$file, isExecutable=$isExecutable)"
}

data class InMemoryDistFileContent(@JvmField val data: ByteArray) : DistFileContent {
  override fun readAsStringForDebug(): String = String(data, 0, data.size.coerceAtMost(1024), Charsets.UTF_8)

  override fun equals(other: Any?): Boolean = this === other || other is InMemoryDistFileContent && data.contentEquals(other.data)

  override fun hashCode(): Int = data.contentHashCode()

  override fun toString(): String = "InMemoryDistFileContent(size=${data.size})"
}

data class DistFile(
  @JvmField val content: DistFileContent,
  @JvmField val relativePath: String,
  @JvmField val os: OsFamily? = null,
  @JvmField val libcImpl: LibcImpl? = null,
  @JvmField val arch: JvmArchitecture? = null,
)

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.product.serialization.RawProductModules
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.util.containers.with
import com.intellij.util.text.SemVer
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Deferred
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.ApplicationInfoPropertiesImpl
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuiltinModulesFileData
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.ContentModuleFilter
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.FrontendModuleFilter
import org.jetbrains.intellij.build.JarPackagerDependencyHelper
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLATFORM_LOADER_JAR
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.computeAppInfoXml
import org.jetbrains.intellij.build.findProductModulesFile
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.plugins.PluginAutoPublishList
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.jarCache.JarCacheManager
import org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.intellij.build.productRunner.createDevModeProductRunner
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.pathString
import kotlin.time.Duration

@Suppress("SpellCheckingInspection")
private val PLUGIN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

class BuildContextImpl internal constructor(
  internal val compilationContext: CompilationContext,
  override val productProperties: ProductProperties,
  override val windowsDistributionCustomizer: WindowsDistributionCustomizer?,
  override val linuxDistributionCustomizer: LinuxDistributionCustomizer?,
  override val macDistributionCustomizer: MacDistributionCustomizer?,
  override val proprietaryBuildTools: ProprietaryBuildTools,
  override val applicationInfo: ApplicationInfoProperties = ApplicationInfoPropertiesImpl(
    project = compilationContext.project,
    productProperties = productProperties,
    buildOptions = compilationContext.options
  ),
  @JvmField internal val jarCacheManager: JarCacheManager,
) : BuildContext, CompilationContext by compilationContext {
  private val distFiles = ConcurrentLinkedQueue<DistFile>()

  private val extraExecutablePatterns = AtomicReference<Map<OsFamily, PersistentList<String>>>(java.util.Map.of())

  override val fullBuildNumber: String
    get() = "${applicationInfo.productCode}-$buildNumber"

  override val systemSelector: String
    get() = productProperties.getSystemSelector(applicationInfo, buildNumber)

  override val buildNumber: String by lazy {
    options.buildNumber ?: SnapshotBuildNumber.VALUE
  }

  override val pluginBuildNumber: String by lazy {
    var value = buildNumber
    if (value.endsWith(SnapshotBuildNumber.SNAPSHOT_SUFFIX)) {
      val buildDate = ZonedDateTime.ofInstant(Instant.ofEpochSecond(options.buildDateInSeconds), ZoneOffset.UTC)
      value = value.replace(SnapshotBuildNumber.SNAPSHOT_SUFFIX, "." + PLUGIN_DATE_FORMAT.format(buildDate))
    }
    if (isNightly(value)) {
      value = "$value.0"
    }
    check(SemVer.parseFromText(value) != null) {
      "The plugin build number $value is expected to match the Semantic Versioning, see https://semver.org"
    }
    value
  }

  override fun reportDistributionBuildNumber() {
    val suppliedBuildNumber = options.buildNumber
    val baseBuildNumber = SnapshotBuildNumber.BASE
    check(suppliedBuildNumber == null || suppliedBuildNumber.startsWith(baseBuildNumber)) {
      "Supplied build number '$suppliedBuildNumber' is expected to start with '$baseBuildNumber' base build number " +
      "defined in ${SnapshotBuildNumber.PATH}"
    }
    messages.setParameter("build.artifact.buildNumber", buildNumber)
    if (buildNumber != suppliedBuildNumber) {
      messages.reportBuildNumber(buildNumber)
    }
  }

  override suspend fun cleanupJarCache() {
    jarCacheManager.cleanup()
  }

  override var bootClassPathJarNames: List<String> = listOf(PLATFORM_LOADER_JAR)

  override val ideMainClassName: String
    get() = if (useModularLoader) "com.intellij.platform.runtime.loader.IntellijLoader" else productProperties.mainClassName

  override val useModularLoader: Boolean
    get() = productProperties.rootModuleForModularLoader != null && options.useModularLoader

  override val generateRuntimeModuleRepository: Boolean
    get() = useModularLoader || options.generateRuntimeModuleRepository

  private var builtinModulesData: BuiltinModulesFileData? = null

  internal val jarPackagerDependencyHelper: JarPackagerDependencyHelper by lazy { JarPackagerDependencyHelper(this.compilationContext) }

  override val nonBundledPlugins: Path by lazy { paths.artifactDir.resolve("${applicationInfo.productCode}-plugins") }

  override val nonBundledPluginsToBePublished: Path by lazy { nonBundledPlugins.resolve("auto-uploading") }

  override val bundledRuntime: BundledRuntime = BundledRuntimeImpl(this)

  override val isNightlyBuild: Boolean = options.isNightlyBuild || isNightly(buildNumber)

  init {
    @Suppress("DEPRECATION")
    if (productProperties.productCode == null) {
      productProperties.productCode = applicationInfo.productCode
    }
    check(!systemSelector.contains(' ')) {
      "System selector must not contain spaces: $systemSelector"
    }
    options.buildStepsToSkip += productProperties.incompatibleBuildSteps
    if (!options.buildStepsToSkip.isEmpty()) {
      Span.current().addEvent(
        "build steps to be skipped",
        Attributes.of(AttributeKey.stringArrayKey("stepsToSkip"), java.util.List.copyOf(options.buildStepsToSkip))
      )
    }
    if (!options.compatiblePluginsToIgnore.isEmpty()) {
      productProperties.productLayout.compatiblePluginsToIgnore =
        productProperties.productLayout.compatiblePluginsToIgnore.addAll(options.compatiblePluginsToIgnore)
    }
    check(options.isInDevelopmentMode || bundledRuntime.prefix == productProperties.runtimeDistribution.artifactPrefix || LibcImpl.current(OsFamily.currentOs) == LinuxLibcImpl.MUSL) {
      "The runtime type doesn't match the one specified in the product properties: ${bundledRuntime.prefix} != ${productProperties.runtimeDistribution.artifactPrefix}"
    }
  }

  companion object {
    suspend fun createContext(
      projectHome: Path,
      productProperties: ProductProperties,
      setupTracer: Boolean = true,
      proprietaryBuildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
      options: BuildOptions = BuildOptions(),
    ): BuildContext {
      val compilationContext = CompilationContextImpl.createCompilationContext(
        projectHome = projectHome,
        buildOutputRootEvaluator = createBuildOutputRootEvaluator(projectHome, productProperties, options),
        options = options,
        setupTracer = setupTracer
      ).asBazelIfNeeded
      return createContext(
        compilationContext = compilationContext,
        projectHome = projectHome,
        productProperties = productProperties,
        proprietaryBuildTools = proprietaryBuildTools,
      )
    }

    fun createContext(
      compilationContext: CompilationContext,
      projectHome: Path,
      productProperties: ProductProperties,
      proprietaryBuildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
    ): BuildContextImpl {
      val projectHomeAsString = projectHome.invariantSeparatorsPathString
      val jarCacheManager = compilationContext.options.jarCacheDir?.let {
        LocalDiskJarCacheManager(cacheDir = it, productionClassOutDir = compilationContext.classesOutputDirectory.resolve("production"))
      } ?: NonCachingJarCacheManager
      return BuildContextImpl(
        compilationContext = compilationContext.asArchivedIfNeeded,
        productProperties = productProperties,
        windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeAsString),
        linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeAsString),
        macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeAsString),
        proprietaryBuildTools = proprietaryBuildTools,
        applicationInfo = ApplicationInfoPropertiesImpl(project = compilationContext.project, productProperties = productProperties, buildOptions = compilationContext.options),
        jarCacheManager = jarCacheManager,
      )
    }
  }

  override var builtinModule: BuiltinModulesFileData?
    get() {
      if (options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
        return null
      }
      else {
        return builtinModulesData ?: throw IllegalStateException("builtinModulesData is not set. Make sure `BuildTasksImpl.buildProvidedModuleList` was called before")
      }
    }
    set(value) {
      check(builtinModulesData == null) { "builtinModulesData was already set" }
      builtinModulesData = value
    }

  override fun addDistFile(file: DistFile) {
    Span.current().addEvent("add app resource", Attributes.of(AttributeKey.stringKey("file"), file.toString()))

    val existing = distFiles.firstOrNull { it.os == file.os && it.arch == file.arch && it.libcImpl == file.libcImpl && it.relativePath == file.relativePath }
    check(existing == null) {
      "$file duplicates $existing"
    }
    distFiles.add(file)
  }

  override fun getBundledPluginModules(): List<String> {
    return bundledPluginModulesForModularLoader ?: productProperties.productLayout.bundledPluginModules
  }

  private val bundledPluginModulesForModularLoader by lazy {
    productProperties.rootModuleForModularLoader?.let { rootModule ->
      loadRawProductModules(rootModule, productProperties.productMode).bundledPluginMainModules.map {
        it.stringId
      }
    }
  }

  override fun getDistFiles(os: OsFamily?, arch: JvmArchitecture?, libcImpl: LibcImpl?): Collection<DistFile> {
    val result = distFiles.filterTo(mutableListOf()) {
      (os == null && arch == null && libcImpl == null) ||
      (os == null || it.os == null || it.os == os) &&
      (arch == null || it.arch == null || it.arch == arch) &&
      (libcImpl == null || it.libcImpl == null || it.libcImpl == libcImpl)
    }
    result.sortWith(compareBy({ it.relativePath }, { it.os }, { it.arch }))
    return result
  }

  override fun findApplicationInfoModule(): JpsModule = findRequiredModule(productProperties.applicationInfoModule)

  override fun notifyArtifactBuilt(artifactPath: Path) {
    compilationContext.notifyArtifactBuilt(artifactPath)
  }

  private val _frontendModuleFilter by lazy {
    val rootModule = productProperties.embeddedFrontendRootModule
    if (rootModule != null && options.enableEmbeddedFrontend) {
      val productModules = loadRawProductModules(rootModule, ProductMode.FRONTEND)
      FrontendModuleFilterImpl.createFrontendModuleFilter(project = project, productModules = productModules, context = this@BuildContextImpl)
    }
    else {
      EmptyFrontendModuleFilter
    }
  }

  override fun getFrontendModuleFilter(): FrontendModuleFilter = _frontendModuleFilter

  private val _contentModuleFilter by lazy { computeContentModuleFilter() }

  private fun computeContentModuleFilter(): ContentModuleFilter {
    if (productProperties.productMode == ProductMode.MONOLITH) {
      if (productProperties.productLayout.skipUnresolvedContentModules) {
        return SkipUnresolvedOptionalContentModuleFilter(context = this)
      }
      return IncludeAllContentModuleFilter
    }

    val bundledPluginModules = getBundledPluginModules()
    return ContentModuleByProductModeFilter(project = project, bundledPluginModules = bundledPluginModules, productMode = productProperties.productMode)
  }

  override fun getContentModuleFilter(): ContentModuleFilter = _contentModuleFilter

  override val isEmbeddedFrontendEnabled: Boolean
    get() = productProperties.embeddedFrontendRootModule != null && options.enableEmbeddedFrontend

  override fun shouldBuildDistributions(): Boolean = !options.targetOs.isEmpty()

  override fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean {
    return shouldBuildDistributions() && options.targetOs.contains(os) && (options.targetArch == null || options.targetArch == arch)
  }

  override suspend fun createCopyForProduct(
    productProperties: ProductProperties,
    projectHomeForCustomizers: Path,
    prepareForBuild: Boolean,
  ): BuildContext {
    val projectHomeForCustomizersAsString = projectHomeForCustomizers.invariantSeparatorsPathString
    val sourceOptions = this.options
    val options = if (options.useCompiledClassesFromProjectOutput) {
      // compiled classes are already reused
      sourceOptions.copy(
        pathToCompiledClassesArchive = null,
        pathToCompiledClassesArchivesMetadata = null,
      )
    }
    else {
      sourceOptions.copy()
    }
    options.targetArch = sourceOptions.targetArch
    options.targetOs = sourceOptions.targetOs

    val newAppInfo = ApplicationInfoPropertiesImpl(project, productProperties, options)

    val buildOut = options.outRootDir ?: createBuildOutputRootEvaluator(paths.projectHome, productProperties, options)(project)
    @Suppress("DEPRECATION")
    val artifactDir = if (prepareForBuild) paths.artifactDir.resolve(productProperties.productCode ?: newAppInfo.productCode) else null
    val compilationContextCopy = compilationContext.createCopy(
      messages, options, computeBuildPaths(options = options, buildOut = buildOut, projectHome = paths.projectHome, artifactDir = artifactDir)
    )
    val copy = BuildContextImpl(
      compilationContext = compilationContextCopy,
      productProperties = productProperties,
      windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeForCustomizersAsString),
      linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizersAsString),
      macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizersAsString),
      proprietaryBuildTools = proprietaryBuildTools,
      applicationInfo = newAppInfo,
      jarCacheManager = jarCacheManager
    )
    if (prepareForBuild) {
      copy.compilationContext.prepareForBuild()
    }
    return copy
  }

  override fun patchInspectScript(path: Path) {
    //todo use placeholder in inspect.sh/inspect.bat file instead
    Files.writeString(path, Files.readString(path).replace(" inspect ", " ${productProperties.inspectCommandName} "))
  }

  override fun getAdditionalJvmArguments(os: OsFamily, arch: JvmArchitecture, isScript: Boolean, isPortableDist: Boolean, isQodana: Boolean): List<String> {
    val jvmArgs = ArrayList<String>()

    val macroName = when (os) {
      OsFamily.WINDOWS -> "%IDE_HOME%"
      OsFamily.MACOS -> $$"$APP_PACKAGE$${if (isPortableDist) "" else "/Contents"}"
      OsFamily.LINUX -> $$"$IDE_HOME"
    }
    val useMultiRoutingFs = !isQodana && isMultiRoutingFileSystemEnabledForProduct(productProperties.platformPrefix)

    val bcpJarNames = productProperties.xBootClassPathJarNames + if (useMultiRoutingFs) listOf(PLATFORM_CORE_NIO_FS) else emptyList()
    if (bcpJarNames.isNotEmpty()) {
      val (pathSeparator, dirSeparator) = if (os == OsFamily.WINDOWS) ";" to "\\" else ":" to "/"
      val bootCp = bcpJarNames.joinToString(pathSeparator) { arrayOf(macroName, "lib", it).joinToString(dirSeparator) }
      jvmArgs.add("-Xbootclasspath/a:${bootCp}".let { if (isScript) '"' + it + '"' else it })
    }

    if (productProperties.enableCds) {
      val cacheDir = if (os == OsFamily.WINDOWS) "%IDE_CACHE_DIR%\\" else $$"$IDE_CACHE_DIR/"
      jvmArgs.add("-XX:SharedArchiveFile=${cacheDir}${productProperties.baseFileName}${buildNumber}.jsa")
      jvmArgs.add("-XX:+AutoCreateSharedArchive")
    }
    else {
      productProperties.classLoader?.let {
        jvmArgs.add("-Djava.system.class.loader=${it}")
      }
    }

    jvmArgs.add("-Didea.vendor.name=${applicationInfo.shortCompanyName}")
    jvmArgs.add("-Didea.paths.selector=${systemSelector}")

    // require bundled JNA dispatcher lib
    jvmArgs.add("-Djna.boot.library.path=${macroName}/lib/jna/${arch.dirName}".let { if (isScript) '"' + it + '"' else it })
    jvmArgs.add("-Djna.nosys=true")
    jvmArgs.add("-Djna.noclasspath=true")
    jvmArgs.add("-Dpty4j.preferred.native.folder=${macroName}/lib/pty4j".let { if (isScript) '"' + it + '"' else it })
    jvmArgs.add("-Dio.netty.allocator.type=pooled")

    if (useModularLoader || generateRuntimeModuleRepository) {
      jvmArgs.add("-Dintellij.platform.runtime.repository.path=${macroName}/${MODULE_DESCRIPTORS_COMPACT_PATH}".let { if (isScript) '"' + it + '"' else it })
    }
    if (useModularLoader) {
      jvmArgs.add("-Dintellij.platform.root.module=${productProperties.rootModuleForModularLoader!!}")
      jvmArgs.add("-Dintellij.platform.product.mode=${productProperties.productMode.id}")
    }

    if (productProperties.platformPrefix != null) {
      jvmArgs.add("-Didea.platform.prefix=${productProperties.platformPrefix}")
    }

    if (os == OsFamily.WINDOWS) {
      jvmArgs.add("-D${IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY}=${useMultiRoutingFs}")
      if (useMultiRoutingFs) {
        jvmArgs.addAll(MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS)
      }
    }

    jvmArgs.addAll(productProperties.additionalIdeJvmArguments)
    jvmArgs.addAll(productProperties.getAdditionalContextDependentIdeJvmArguments(this))

    if (productProperties.useSplash) {
      @Suppress("SpellCheckingInspection", "RedundantSuppression")
      jvmArgs.add("-Dsplash=true")
    }

    // https://youtrack.jetbrains.com/issue/IDEA-269280
    jvmArgs.add("-Daether.connector.resumeDownloads=false")

    jvmArgs.add("-Dcompose.swing.render.on.graphics=true")

    if (bundledRuntime.version >= 25) {
      jvmArgs.add("--enable-native-access=ALL-UNNAMED")
    }

    jvmArgs.addAll(getCommandLineArgumentsForOpenPackages(context = this, os))

    return jvmArgs
  }

  override fun addExtraExecutablePattern(os: OsFamily, pattern: String) {
    extraExecutablePatterns.updateAndGet { prev ->
      prev.with(os, (prev.get(os) ?: persistentListOf()).add(pattern))
    }
  }

  override fun getExtraExecutablePattern(os: OsFamily): List<String> = extraExecutablePatterns.get()[os] ?: listOf()

  override val appInfoXml: String by lazy {
    computeAppInfoXml(context = this, appInfo = applicationInfo)
  }

  override fun loadRawProductModules(rootModuleName: String, productMode: ProductMode): RawProductModules {
    val productModulesFile = findProductModulesFile(clientMainModuleName = rootModuleName, context = this)
                             ?: error("Cannot find product-modules.xml file in $rootModuleName")
    val resolver = object : ResourceFileResolver {
      override fun readResourceFile(moduleId: RuntimeModuleId, relativePath: String): InputStream? {
        return findFileInModuleSources(findRequiredModule(moduleId.stringId), relativePath)?.inputStream()
      }

      override fun toString(): String {
        return "source file based resolver for '${paths.projectHome}' project"
      }
    }
    return ProductModulesSerialization.readProductModulesAndMergeIncluded(productModulesFile.inputStream(), productModulesFile.pathString, resolver)
  }

  private val devModeProductRunner = asyncLazy("dev mode product runner") {
    createDevModeProductRunner(this@BuildContextImpl)
  }

  override suspend fun createProductRunner(additionalPluginModules: List<String>): IntellijProductRunner {
    return when {
      additionalPluginModules.isEmpty() -> devModeProductRunner.await()
      else -> createDevModeProductRunner(additionalPluginModules = additionalPluginModules, context = this)
    }
  }

  override suspend fun runProcess(
    args: List<String>,
    workingDir: Path?,
    timeout: Duration,
    additionalEnvVariables: Map<String, String>,
    attachStdOutToException: Boolean,
  ) {
    runProcess(
      args = args,
      workingDir = workingDir,
      timeout = timeout,
      additionalEnvVariables = additionalEnvVariables,
      attachStdOutToException = attachStdOutToException,
      stdOutConsumer = messages::info,
      stdErrConsumer = messages::warning,
    )
  }

  override val pluginAutoPublishList: PluginAutoPublishList by lazy {
    PluginAutoPublishList(this)
  }

  private val distributionState: Deferred<DistributionBuilderState> = asyncLazy("Creating distribution state") {
    createDistributionState(this@BuildContextImpl)
  }

  override suspend fun distributionState(): DistributionBuilderState {
    return distributionState.await()
  }
}

private fun createBuildOutputRootEvaluator(projectHome: Path, productProperties: ProductProperties, buildOptions: BuildOptions): (JpsProject) -> Path {
  return { project ->
    val appInfo = ApplicationInfoPropertiesImpl(project, productProperties, buildOptions)
    projectHome.resolve("out/${productProperties.getOutputDirectoryName(appInfo)}")
  }
}

private fun isNightly(buildNumber: String): Boolean {
  return buildNumber.count { it == '.' } <= 1
}
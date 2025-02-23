// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashStream64
import com.intellij.platform.ijent.community.buildConstants.IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.ijent.community.buildConstants.isIjentWslFsEnabledByDefaultForProduct
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.util.containers.with
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.plugins.PluginAutoPublishList
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.jarCache.JarCacheManager
import org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheManager
import org.jetbrains.intellij.build.jarCache.NonCachingJarCacheManager
import org.jetbrains.intellij.build.jarCache.SourceBuilder
import org.jetbrains.intellij.build.productRunner.IntellijProductRunner
import org.jetbrains.intellij.build.productRunner.ModuleBasedProductRunner
import org.jetbrains.intellij.build.productRunner.createDevModeProductRunner
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration

class BuildContextImpl internal constructor(
  internal val compilationContext: CompilationContext,
  override val productProperties: ProductProperties,
  override val windowsDistributionCustomizer: WindowsDistributionCustomizer?,
  override val linuxDistributionCustomizer: LinuxDistributionCustomizer?,
  override val macDistributionCustomizer: MacDistributionCustomizer?,
  override val proprietaryBuildTools: ProprietaryBuildTools,
  override val applicationInfo: ApplicationInfoProperties = ApplicationInfoPropertiesImpl(
    compilationContext.project, productProperties, compilationContext.options
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
    options.pluginBuildNumber ?: buildNumber
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

  override val isNightlyBuild: Boolean = options.isNightlyBuild || buildNumber.count { it == '.' } <= 1

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
    check(options.isInDevelopmentMode || bundledRuntime.prefix == productProperties.runtimeDistribution.artifactPrefix) {
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
        projectHome, createBuildOutputRootEvaluator(projectHome, productProperties, options), options, setupTracer
      )
      return createContext(compilationContext, projectHome, productProperties, proprietaryBuildTools)
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
        applicationInfo = ApplicationInfoPropertiesImpl(
          project = compilationContext.project,
          productProperties = productProperties,
          buildOptions = compilationContext.options,
        ),
        jarCacheManager = jarCacheManager,
      )
    }
  }

  override var builtinModule: BuiltinModulesFileData?
    get() {
      if (options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
        return null
      }
      return builtinModulesData ?: throw IllegalStateException("builtinModulesData is not set. Make sure `BuildTasksImpl.buildProvidedModuleList` was called before")
    }
    set(value) {
      check(builtinModulesData == null) { "builtinModulesData was already set" }
      builtinModulesData = value
    }

  override fun addDistFile(file: DistFile) {
    Span.current().addEvent("add app resource", Attributes.of(AttributeKey.stringKey("file"), file.toString()))

    val existing = distFiles.firstOrNull { it.os == file.os && it.arch == file.arch && it.relativePath == file.relativePath }
    check(existing == null) {
      "$file duplicates $existing"
    }
    distFiles.add(file)
  }

  override suspend fun getBundledPluginModules(): List<String> {
    return bundledPluginModulesForModularLoader.await() ?: productProperties.productLayout.bundledPluginModules
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val bundledPluginModulesForModularLoader = GlobalScope.async(Dispatchers.Unconfined + CoroutineName("bundled plugin modules for modular loader"), start = CoroutineStart.LAZY) {
    productProperties.rootModuleForModularLoader?.let { rootModule ->
      getOriginalModuleRepository().loadRawProductModules(rootModule, productProperties.productMode).bundledPluginMainModules.map {
        it.stringId
      }
    }
  }

  override fun getDistFiles(os: OsFamily?, arch: JvmArchitecture?): Collection<DistFile> {
    val result = distFiles.filterTo(mutableListOf()) {
      (os == null && arch == null) ||
      (os == null || it.os == null || it.os == os) &&
      (arch == null || it.arch == null || it.arch == arch)
    }
    result.sortWith(compareBy({ it.relativePath }, { it.os }, { it.arch }))
    return result
  }

  override fun findApplicationInfoModule(): JpsModule = findRequiredModule(productProperties.applicationInfoModule)

  override fun notifyArtifactBuilt(artifactPath: Path) {
    compilationContext.notifyArtifactBuilt(artifactPath)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val _frontendModuleFilter = GlobalScope.async(Dispatchers.Unconfined + CoroutineName("JetBrains client module filter"), start = CoroutineStart.LAZY) {
    val rootModule = productProperties.embeddedFrontendRootModule
    if (rootModule != null && options.enableEmbeddedFrontend) {
      val moduleRepository = getOriginalModuleRepository()
      val productModules = moduleRepository.loadProductModules(rootModule, ProductMode.FRONTEND)
      FrontendModuleFilterImpl(moduleRepository.repository, productModules)
    }
    else {
      EmptyFrontendModuleFilter
    }
  }

  override suspend fun getFrontendModuleFilter(): FrontendModuleFilter = _frontendModuleFilter.await()

  private val contentModuleFilter = computeContentModuleFilter()

  @OptIn(DelicateCoroutinesApi::class)
  private fun computeContentModuleFilter(): Deferred<ContentModuleFilter> {
    if (productProperties.productMode == ProductMode.MONOLITH) {
      if (productProperties.productLayout.skipUnresolvedContentModules) {
        return CompletableDeferred(SkipUnresolvedOptionalContentModuleFilter(context = this))
      }
      return CompletableDeferred(IncludeAllContentModuleFilter)
    }
    
    return GlobalScope.async(Dispatchers.Unconfined + CoroutineName("Content Modules Filter"), start = CoroutineStart.LAZY) {
      val bundledPluginModules = getBundledPluginModules()
      ContentModuleByProductModeFilter(getOriginalModuleRepository().repository, bundledPluginModules, productProperties.productMode)
    }
  }

  override suspend fun getContentModuleFilter(): ContentModuleFilter = contentModuleFilter.await()

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

    val newAppInfo = ApplicationInfoPropertiesImpl(project = project, productProperties = productProperties, buildOptions = options)

    val compilationContextCopy = compilationContext.createCopy(
      messages = messages,
      options = options,
      paths = computeBuildPaths(
        options = options,
        buildOut = options.outRootDir ?: createBuildOutputRootEvaluator(paths.projectHome, productProperties, options)(project),
        projectHome = paths.projectHome,
        artifactDir = if (prepareForBuild) {
          @Suppress("DEPRECATION")
          paths.artifactDir.resolve(productProperties.productCode ?: newAppInfo.productCode)
        }
        else {
          null
        }
      )
    )
    val copy = BuildContextImpl(
      compilationContext = compilationContextCopy,
      productProperties = productProperties,
      windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeForCustomizersAsString),
      linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizersAsString),
      macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizersAsString),
      proprietaryBuildTools = proprietaryBuildTools,
      applicationInfo = newAppInfo,
      jarCacheManager = jarCacheManager,
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

  @Suppress("SpellCheckingInspection")
  override fun getAdditionalJvmArguments(os: OsFamily, arch: JvmArchitecture, isScript: Boolean, isPortableDist: Boolean, isQodana: Boolean): List<String> {
    val jvmArgs = ArrayList<String>()
    val macroName = when (os) {
      OsFamily.WINDOWS -> "%IDE_HOME%"
      OsFamily.MACOS -> "\$APP_PACKAGE${if (isPortableDist) "" else "/Contents"}"
      OsFamily.LINUX -> "\$IDE_HOME"
    }
    val useMultiRoutingFs = !isQodana && isIjentWslFsEnabledByDefaultForProduct(productProperties.platformPrefix)

    val bcpJarNames = productProperties.xBootClassPathJarNames + if (useMultiRoutingFs) listOf(PLATFORM_CORE_NIO_FS) else emptyList()
    if (bcpJarNames.isNotEmpty()) {
      val separator = if (os == OsFamily.WINDOWS) ";" else ":"
      val bootCp = bcpJarNames.joinToString(separator) { "${macroName}/lib/${it}" }
      jvmArgs += "-Xbootclasspath/a:${bootCp}".let { if (isScript) '"' + it + '"' else it }
    }

    if (productProperties.enableCds) {
      val cacheDir = if (os == OsFamily.WINDOWS) "%IDE_CACHE_DIR%\\" else "\$IDE_CACHE_DIR/"
      jvmArgs += "-XX:SharedArchiveFile=${cacheDir}${productProperties.baseFileName}${buildNumber}.jsa"
      jvmArgs += "-XX:+AutoCreateSharedArchive"
    }
    else {
      productProperties.classLoader?.let {
        jvmArgs += "-Djava.system.class.loader=${it}"
      }
    }

    jvmArgs += "-Didea.vendor.name=${applicationInfo.shortCompanyName}"
    jvmArgs += "-Didea.paths.selector=${systemSelector}"

    // require bundled JNA dispatcher lib
    jvmArgs += "-Djna.boot.library.path=${macroName}/lib/jna/${arch.dirName}".let { if (isScript) '"' + it + '"' else it }
    jvmArgs += "-Djna.nosys=true"
    jvmArgs += "-Djna.noclasspath=true"
    jvmArgs += "-Dpty4j.preferred.native.folder=${macroName}/lib/pty4j".let { if (isScript) '"' + it + '"' else it }
    jvmArgs += "-Dio.netty.allocator.type=pooled"

    if (useModularLoader || generateRuntimeModuleRepository) {
      jvmArgs += "-Dintellij.platform.runtime.repository.path=${macroName}/${MODULE_DESCRIPTORS_JAR_PATH}".let { if (isScript) '"' + it + '"' else it }
    }
    if (useModularLoader) {
      jvmArgs += "-Dintellij.platform.root.module=${productProperties.rootModuleForModularLoader!!}"
      jvmArgs += "-Dintellij.platform.product.mode=${productProperties.productMode.id}"
    }

    if (productProperties.platformPrefix != null) {
      jvmArgs += "-Didea.platform.prefix=${productProperties.platformPrefix}"
    }

    if (os == OsFamily.WINDOWS) {
      jvmArgs += "-D${IJENT_WSL_FILE_SYSTEM_REGISTRY_KEY}=${useMultiRoutingFs}"
      if (useMultiRoutingFs) {
        jvmArgs += MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
      }
    }

    jvmArgs += productProperties.additionalIdeJvmArguments
    jvmArgs += productProperties.getAdditionalContextDependentIdeJvmArguments(this)

    if (productProperties.useSplash) {
      @Suppress("SpellCheckingInspection", "RedundantSuppression")
      jvmArgs += ("-Dsplash=true")
    }

    // https://youtrack.jetbrains.com/issue/IDEA-269280
    jvmArgs += "-Daether.connector.resumeDownloads=false"

    jvmArgs += "-Dcompose.swing.render.on.graphics=true"

    jvmArgs += getCommandLineArgumentsForOpenPackages(context = this, os)

    return jvmArgs
  }

  override fun addExtraExecutablePattern(os: OsFamily, pattern: String) {
    extraExecutablePatterns.updateAndGet { prev ->
      prev.with(os, (prev[os] ?: persistentListOf()).add(pattern))
    }
  }

  override fun getExtraExecutablePattern(os: OsFamily): List<String> = extraExecutablePatterns.get()[os] ?: listOf()

  override suspend fun buildJar(targetFile: Path, sources: List<Source>, compress: Boolean) {
    jarCacheManager.computeIfAbsent(
      sources = sources,
      targetFile = targetFile,
      nativeFiles = null,
      span = Span.current(),
      producer = object : SourceBuilder {
        override val useCacheAsTargetFile: Boolean
          get() = false

        override fun updateDigest(digest: HashStream64) {
          digest.putInt(-1)
        }

        override suspend fun produce(targetFile: Path) {
          buildJar(targetFile = targetFile, sources = sources, compress = compress, notify = false)
        }
      },
    )
  }

  override val appInfoXml: String by lazy {
    computeAppInfoXml(context = this, appInfo = applicationInfo)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val devModeProductRunner = GlobalScope.async(Dispatchers.Unconfined + CoroutineName("dev mode product runner"), start = CoroutineStart.LAZY) {
    createDevModeProductRunner(this@BuildContextImpl)
  }

  override suspend fun createProductRunner(additionalPluginModules: List<String>): IntellijProductRunner {
    when {
      useModularLoader -> return ModuleBasedProductRunner(productProperties.rootModuleForModularLoader!!, this)
      additionalPluginModules.isEmpty() -> return devModeProductRunner.await()
      else -> return createDevModeProductRunner(additionalPluginModules = additionalPluginModules, context = this)
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
      stdOutConsumer = messages::info,
      stdErrConsumer = messages::warning,
      attachStdOutToException = attachStdOutToException,
    )
  }

  override val pluginAutoPublishList: PluginAutoPublishList by lazy {
    PluginAutoPublishList(this)
  }
}

private fun createBuildOutputRootEvaluator(projectHome: Path, productProperties: ProductProperties, buildOptions: BuildOptions): (JpsProject) -> Path {
  return { project ->
    val appInfo = ApplicationInfoPropertiesImpl(project, productProperties, buildOptions)
    projectHome.resolve("out/${productProperties.getOutputDirectoryName(appInfo)}")
  }
}

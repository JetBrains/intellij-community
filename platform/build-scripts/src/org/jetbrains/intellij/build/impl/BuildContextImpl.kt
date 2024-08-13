// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.dynatrace.hash4j.hashing.HashStream64
import com.intellij.util.containers.with
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.*
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
import org.jetbrains.intellij.build.io.runProcess
import kotlin.time.Duration

class BuildContextImpl internal constructor(
  private val compilationContext: CompilationContext,
  override val productProperties: ProductProperties,
  override val windowsDistributionCustomizer: WindowsDistributionCustomizer?,
  override val linuxDistributionCustomizer: LinuxDistributionCustomizer?,
  override val macDistributionCustomizer: MacDistributionCustomizer?,
  override val proprietaryBuildTools: ProprietaryBuildTools,
  override val applicationInfo: ApplicationInfoProperties = ApplicationInfoPropertiesImpl(
    project = compilationContext.project,
    productProperties = productProperties,
    buildOptions = compilationContext.options,
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

  override fun checkDistributionBuildNumber() {
    val suppliedBuildNumber = options.buildNumber
    val baseBuildNumber = SnapshotBuildNumber.VALUE.removeSuffix(".SNAPSHOT")
    check(suppliedBuildNumber == null || suppliedBuildNumber.startsWith(baseBuildNumber)) {
      "Supplied build number '$suppliedBuildNumber' is expected to start with '$baseBuildNumber' base build number " +
      "defined in ${SnapshotBuildNumber.PATH}"
    }
  }

  override suspend fun cleanupJarCache() {
    jarCacheManager.cleanup()
  }

  override val xBootClassPathJarNames: List<String>
    get() = productProperties.xBootClassPathJarNames

  override var bootClassPathJarNames: List<String> = java.util.List.of(PLATFORM_LOADER_JAR)
  
  override val ideMainClassName: String
    get() = if (useModularLoader) "com.intellij.platform.runtime.loader.IntellijLoader" else productProperties.mainClassName
  
  override val useModularLoader: Boolean
    get() = productProperties.rootModuleForModularLoader != null && options.useModularLoader

  override val generateRuntimeModuleRepository: Boolean
    get() = useModularLoader || options.generateRuntimeModuleRepository

  private var builtinModulesData: BuiltinModulesFileData? = null

  internal val jarPackagerDependencyHelper: JarPackagerDependencyHelper by lazy { JarPackagerDependencyHelper(this) }

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
      Span.current().addEvent("build steps to be skipped", Attributes.of(
        AttributeKey.stringArrayKey("stepsToSkip"), java.util.List.copyOf(options.buildStepsToSkip)
      ))
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
        setupTracer = setupTracer,
        buildOutputRootEvaluator = createBuildOutputRootEvaluator(projectHome = projectHome, productProperties = productProperties, buildOptions = options),
        options = options,
      )
      return createContext(compilationContext = compilationContext, projectHome = projectHome, productProperties = productProperties, proprietaryBuildTools = proprietaryBuildTools)
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
        compilationContext = compilationContext,
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

  override val bundledPluginModules: List<String>
    get() = bundledPluginModulesForModularLoader ?: productProperties.productLayout.bundledPluginModules
  
  private val bundledPluginModulesForModularLoader by lazy {
    productProperties.rootModuleForModularLoader?.let { rootModule ->
      originalModuleRepository.loadRawProductModules(rootModule, productProperties.productMode).bundledPluginMainModules.map { 
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

  override val jetBrainsClientModuleFilter: JetBrainsClientModuleFilter by lazy {
    val mainModule = productProperties.embeddedJetBrainsClientMainModule
    if (mainModule != null && options.enableEmbeddedJetBrainsClient) {
      JetBrainsClientModuleFilterImpl(clientMainModuleName = mainModule, context = this)
    }
    else {
      EmptyJetBrainsClientModuleFilter
    }
  }
  
  override val isEmbeddedJetBrainsClientEnabled: Boolean
    get() = productProperties.embeddedJetBrainsClientMainModule != null && options.enableEmbeddedJetBrainsClient

  override fun shouldBuildDistributions(): Boolean = !options.targetOs.isEmpty()

  override fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean {
    return shouldBuildDistributions() && options.targetOs.contains(os) && (options.targetArch == null || options.targetArch == arch)
  }

  override fun createCopyForProduct(
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
        buildOut = options.outRootDir ?: createBuildOutputRootEvaluator(
          projectHome = paths.projectHome,
          productProperties = productProperties,
          buildOptions = options,
        )(project),
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

  override fun includeBreakGenLibraries() = isJavaSupportedInProduct

  private val isJavaSupportedInProduct: Boolean
    get() = bundledPluginModules.contains(JavaPluginLayout.MAIN_MODULE_NAME)

  override fun patchInspectScript(path: Path) {
    //todo use placeholder in inspect.sh/inspect.bat file instead
    Files.writeString(path, Files.readString(path).replace(" inspect ", " ${productProperties.inspectCommandName} "))
  }

  @Suppress("SpellCheckingInspection")
  override fun getAdditionalJvmArguments(os: OsFamily, arch: JvmArchitecture, isScript: Boolean, isPortableDist: Boolean): List<String> {
    val jvmArgs = ArrayList<String>()

    productProperties.classLoader?.let {
      jvmArgs.add("-Djava.system.class.loader=${it}")
    }

    jvmArgs.add("-Didea.vendor.name=${applicationInfo.shortCompanyName}")
    jvmArgs.add("-Didea.paths.selector=${systemSelector}")

    val macroName = when (os) {
      OsFamily.WINDOWS -> "%IDE_HOME%"
      OsFamily.MACOS -> "\$APP_PACKAGE${if (isPortableDist) "" else "/Contents"}"
      OsFamily.LINUX -> "\$IDE_HOME"
    }
    jvmArgs.add("-Djna.boot.library.path=${macroName}/lib/jna/${arch.dirName}".let { if (isScript) '"' + it + '"' else it })
    jvmArgs.add("-Dpty4j.preferred.native.folder=${macroName}/lib/pty4j".let { if (isScript) '"' + it + '"' else it })
    // require bundled JNA dispatcher lib
    jvmArgs.add("-Djna.nosys=true")
    jvmArgs.add("-Djna.noclasspath=true")

    if (useModularLoader || generateRuntimeModuleRepository) {
      jvmArgs.add("-Dintellij.platform.runtime.repository.path=${macroName}/${MODULE_DESCRIPTORS_JAR_PATH}".let { if (isScript) '"' + it + '"' else it })
    }
    if (useModularLoader) {
      jvmArgs.add("-Dintellij.platform.root.module=${productProperties.rootModuleForModularLoader!!}")
      jvmArgs.add("-Dintellij.platform.product.mode=${productProperties.productMode.id}")
    }

    if (productProperties.platformPrefix != null) {
      jvmArgs.add("-Didea.platform.prefix=${productProperties.platformPrefix}")
    }

    jvmArgs.addAll(productProperties.additionalIdeJvmArguments)
    jvmArgs.addAll(productProperties.getAdditionalContextDependentIdeJvmArguments(this))

    if (productProperties.useSplash) {
      @Suppress("SpellCheckingInspection", "RedundantSuppression")
      jvmArgs.add("-Dsplash=true")
    }

    // https://youtrack.jetbrains.com/issue/IDEA-269280
    jvmArgs.add("-Daether.connector.resumeDownloads=false")

    jvmArgs.add("-Dskiko.library.path=${macroName}/lib/skiko-awt-runtime-all".let { if (isScript) '"' + it + '"' else it })
    jvmArgs.add("-Dcompose.swing.render.on.graphics=true")

    jvmArgs.addAll(getCommandLineArgumentsForOpenPackages(this, os))

    return jvmArgs
  }

  override fun addExtraExecutablePattern(os: OsFamily, pattern: String) {
    extraExecutablePatterns.updateAndGet { prev ->
      prev.with(os, (prev.get(os) ?: persistentListOf()).add(pattern))
    }
  }

  override fun getExtraExecutablePattern(os: OsFamily): List<String> = extraExecutablePatterns.get().get(os) ?: java.util.List.of()

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
          digest.putByte(Byte.MIN_VALUE)
        }

        override suspend fun produce(targetFile: Path) {
          buildJar(targetFile = targetFile, sources = sources, compress = compress, notify = false)
        }
      },
    )
  }

  override val appInfoXml by lazy {
    return@lazy computeAppInfoXml(context = this, appInfo = applicationInfo)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val devModeProductRunner = GlobalScope.async(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
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
    vararg args: String,
    workingDir: Path?,
    timeout: Duration,
    additionalEnvVariables: Map<String, String>,
    attachStdOutToException: Boolean,
  ) {
    runProcess(
      args.toList(),
      workingDir = workingDir,
      timeout = timeout,
      additionalEnvVariables = additionalEnvVariables,
      stdOutConsumer = messages::info,
      stdErrConsumer = messages::warning,
      attachStdOutToException = attachStdOutToException,
    )
  }
}

private fun createBuildOutputRootEvaluator(projectHome: Path, productProperties: ProductProperties, buildOptions: BuildOptions): (JpsProject) -> Path {
  return { project ->
    val appInfo = ApplicationInfoPropertiesImpl(project = project, productProperties = productProperties, buildOptions = buildOptions)
    projectHome.resolve("out/${productProperties.getOutputDirectoryName(appInfo)}")
  }
}

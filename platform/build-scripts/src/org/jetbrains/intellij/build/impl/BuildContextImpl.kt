// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "RAW_RUN_BLOCKING")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Strings
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

class BuildContextImpl(
  private val compilationContext: CompilationContextImpl,
  override val productProperties: ProductProperties,
  override val windowsDistributionCustomizer: WindowsDistributionCustomizer?,
  override val linuxDistributionCustomizer: LinuxDistributionCustomizer?,
  internal val macDistributionCustomizer: MacDistributionCustomizer?,
  override val proprietaryBuildTools: ProprietaryBuildTools,
) : BuildContext, CompilationContext by compilationContext {
  private val distFiles = ConcurrentLinkedQueue<DistFile>()

  private val extraExecutablePatterns = AtomicReference<PersistentMap<OsFamily, PersistentList<String>>>(persistentHashMapOf())

  override val fullBuildNumber: String
    get() = "${applicationInfo.productCode}-$buildNumber"

  override val systemSelector: String
    get() = productProperties.getSystemSelector(applicationInfo, buildNumber)

  override val buildNumber: String = options.buildNumber ?: readSnapshotBuildNumber(paths.communityHomeDirRoot)

  override val xBootClassPathJarNames: List<String>
    get() = productProperties.xBootClassPathJarNames

  override var bootClassPathJarNames = persistentListOf("util.jar", "util_rt.jar")

  override val applicationInfo = ApplicationInfoPropertiesImpl(this)
  private var builtinModulesData: BuiltinModulesFileData? = null

  internal val jarCacheManager: JarCacheManager by lazy {
    options.jarCacheDir?.let { LocalDiskJarCacheManager(it) } ?: NonCachingJarCacheManager
  }

  init {
    @Suppress("DEPRECATION")
    if (productProperties.productCode == null) {
      productProperties.productCode = applicationInfo.productCode
    }
    check(!systemSelector.contains(' ')) {
      "System selector must not contain spaces: $systemSelector"
    }
    options.buildStepsToSkip.addAll(productProperties.incompatibleBuildSteps)
    if (!options.buildStepsToSkip.isEmpty()) {
      Span.current().addEvent("build steps to be skipped", Attributes.of(
        AttributeKey.stringArrayKey("stepsToSkip"), options.buildStepsToSkip.toImmutableList()
      ))
    }
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun createContextBlocking(communityHome: BuildDependenciesCommunityRoot,
                              projectHome: Path,
                              productProperties: ProductProperties,
                              proprietaryBuildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
                              options: BuildOptions = BuildOptions()): BuildContext {
      return runBlocking(Dispatchers.Default) {
        createContext(communityHome = communityHome,
                      projectHome = projectHome,
                      productProperties = productProperties,
                      proprietaryBuildTools = proprietaryBuildTools,
                      options = options)
      }
    }

    suspend fun createContext(communityHome: BuildDependenciesCommunityRoot,
                              projectHome: Path,
                              productProperties: ProductProperties,
                              proprietaryBuildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
                              options: BuildOptions = BuildOptions()): BuildContext {
      val compilationContext = CompilationContextImpl.createCompilationContext(
        communityHome = communityHome,
        projectHome = projectHome,
        setupTracer = true,
        buildOutputRootEvaluator = createBuildOutputRootEvaluator(projectHome, productProperties, options),
        options = options,
      )
      return createContext(compilationContext = compilationContext,
                           projectHome = projectHome,
                           productProperties = productProperties,
                           proprietaryBuildTools = proprietaryBuildTools)
    }

    fun createContext(compilationContext: CompilationContextImpl,
                      projectHome: Path,
                      productProperties: ProductProperties,
                      proprietaryBuildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY): BuildContextImpl {
      val projectHomeAsString = FileUtilRt.toSystemIndependentName(projectHome.toString())
      val windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeAsString)
      val linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeAsString)
      val macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeAsString)
      return BuildContextImpl(compilationContext = compilationContext,
                              productProperties = productProperties,
                              windowsDistributionCustomizer = windowsDistributionCustomizer,
                              linuxDistributionCustomizer = linuxDistributionCustomizer,
                              macDistributionCustomizer = macDistributionCustomizer,
                              proprietaryBuildTools = proprietaryBuildTools)
    }
  }

  override var builtinModule: BuiltinModulesFileData?
    get() {
      if (options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
        return null
      }
      return builtinModulesData ?: throw IllegalStateException("builtinModulesData is not set. " +
                                                               "Make sure `BuildTasksImpl.buildProvidedModuleList` was called before")
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

  override fun getDistFiles(os: OsFamily?, arch: JvmArchitecture?): Collection<DistFile> {
    val result = distFiles.filterTo(mutableListOf()) {
      (os == null && arch == null) ||
      (os == null || it.os == null || it.os == os) &&
      (arch == null || it.arch == null || it.arch == arch)
    }
    result.sortWith(compareBy({ it.relativePath }, { it.os }, { it.arch }))
    return result
  }

  override fun findApplicationInfoModule(): JpsModule {
    return findRequiredModule(productProperties.applicationInfoModule)
  }

  override fun notifyArtifactBuilt(artifactPath: Path) {
    compilationContext.notifyArtifactBuilt(artifactPath)
  }

  override fun findFileInModuleSources(moduleName: String, relativePath: String): Path? {
    return findFileInModuleSources(findRequiredModule(moduleName), relativePath)
  }

  override fun findFileInModuleSources(module: JpsModule, relativePath: String): Path? {
    for (info in getSourceRootsWithPrefixes(module)) {
      if (relativePath.startsWith(info.second)) {
        val result = info.first.resolve(Strings.trimStart(Strings.trimStart(relativePath, info.second), "/"))
        if (Files.exists(result)) {
          return result
        }
      }
    }
    return null
  }

  override fun shouldBuildDistributions(): Boolean = !options.targetOs.isEmpty()

  override fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean {
    return shouldBuildDistributions() && options.targetOs.contains(os) && (options.targetArch == null || options.targetArch == arch)
  }

  override fun createCopyForProduct(productProperties: ProductProperties, projectHomeForCustomizers: Path): BuildContext {
    val projectHomeForCustomizersAsString = FileUtilRt.toSystemIndependentName(projectHomeForCustomizers.toString())
    val options = BuildOptions(compressZipFiles = this.options.compressZipFiles)
    options.useCompiledClassesFromProjectOutput = this.options.useCompiledClassesFromProjectOutput
    options.buildStepsToSkip = this.options.buildStepsToSkip
    options.targetArch = this.options.targetArch
    options.targetOs = this.options.targetOs
    val compilationContextCopy = compilationContext.createCopy(
      messages = messages,
      options = options,
      buildOutputRootEvaluator = createBuildOutputRootEvaluator(paths.projectHome, productProperties, options)
    )
    val copy = BuildContextImpl(
      compilationContext = compilationContextCopy,
      productProperties = productProperties,
      windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeForCustomizersAsString),
      linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizersAsString),
      macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizersAsString),
      proprietaryBuildTools = proprietaryBuildTools,
    )
    @Suppress("DEPRECATION") val productCode = productProperties.productCode
    copy.paths.artifactDir = paths.artifactDir.resolve(productCode!!)
    copy.paths.artifacts = "${paths.artifacts}/$productCode"
    copy.compilationContext.prepareForBuild()
    return copy
  }

  override fun includeBreakGenLibraries() = isJavaSupportedInProduct

  private val isJavaSupportedInProduct: Boolean
    get() = productProperties.productLayout.bundledPluginModules.contains("intellij.java.plugin")

  override fun patchInspectScript(path: Path) {
    //todo[nik] use placeholder in inspect.sh/inspect.bat file instead
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

    if (productProperties.platformPrefix != null) {
      jvmArgs.add("-Didea.platform.prefix=${productProperties.platformPrefix}")
    }

    jvmArgs.addAll(productProperties.additionalIdeJvmArguments)

    if (productProperties.useSplash) {
      @Suppress("SpellCheckingInspection")
      jvmArgs.add("-Dsplash=true")
    }

    jvmArgs.addAll(getCommandLineArgumentsForOpenPackages(this, os))

    return jvmArgs
  }

  override fun addExtraExecutablePattern(os: OsFamily, pattern: String) {
    extraExecutablePatterns.updateAndGet { prev ->
      prev.put(os, (prev.get(os) ?: persistentListOf()).add(pattern))
    }
  }

  override fun getExtraExecutablePattern(os: OsFamily): List<String> = extraExecutablePatterns.get().get(os) ?: emptyList()
}

private fun createBuildOutputRootEvaluator(projectHome: Path,
                                           productProperties: ProductProperties,
                                           buildOptions: BuildOptions): (JpsProject) -> Path {
  return { project ->
    val appInfo = ApplicationInfoPropertiesImpl(project = project,
                                                productProperties = productProperties,
                                                buildOptions = buildOptions)
    projectHome.resolve("out/${productProperties.getOutputDirectoryName(appInfo)}")
  }
}

private fun getSourceRootsWithPrefixes(module: JpsModule): Sequence<Pair<Path, String>> {
  return module.sourceRoots.asSequence()
    .filter { root: JpsModuleSourceRoot ->
      JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType)
    }
    .map { moduleSourceRoot: JpsModuleSourceRoot ->
      var prefix: String
      val properties = moduleSourceRoot.properties
      prefix = if (properties is JavaSourceRootProperties) {
        properties.packagePrefix.replace(".", "/")
      }
      else {
        (properties as JavaResourceRootProperties).relativeOutputPath
      }
      if (!prefix.endsWith("/")) {
        prefix += "/"
      }
      Pair(Path.of(JpsPathUtil.urlToPath(moduleSourceRoot.url)), prefix.trimStart('/'))
    }
}

internal fun readSnapshotBuildNumber(communityHome: BuildDependenciesCommunityRoot): String {
  return Files.readString(communityHome.communityRoot.resolve("build.txt")).trim { it <= ' ' }
}

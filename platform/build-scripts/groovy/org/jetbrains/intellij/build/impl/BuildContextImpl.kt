// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Strings
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.jps.model.JpsModel
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

class BuildContextImpl private constructor(private val compilationContext: CompilationContextImpl,
                                           override val productProperties: ProductProperties,
                                           override val windowsDistributionCustomizer: WindowsDistributionCustomizer?,
                                           override val linuxDistributionCustomizer: LinuxDistributionCustomizer?,
                                           override val macDistributionCustomizer: MacDistributionCustomizer?,
                                           override val proprietaryBuildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
                                           private val distFiles: ConcurrentLinkedQueue<Map.Entry<Path, String>>) : BuildContext {
  override val fullBuildNumber: String
    get() = "${applicationInfo.productCode}-$buildNumber"

  override val systemSelector: String
    get() = productProperties.getSystemSelector(applicationInfo, buildNumber)


  override val buildNumber: String = options.buildNumber ?: readSnapshotBuildNumber(paths.communityHomeDir)

  override val xBootClassPathJarNames: List<String>
    get() = productProperties.xBootClassPathJarNames

  override var bootClassPathJarNames = persistentListOf("util.jar", "util_rt.jar")

  override val applicationInfo: ApplicationInfoProperties = ApplicationInfoPropertiesImpl(project, productProperties, options).patch(this)
  private var builtinModulesData: BuiltinModulesFileData? = null

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
    fun createContext(communityHome: BuildDependenciesCommunityRoot,
                      projectHome: Path,
                      productProperties: ProductProperties,
                      proprietaryBuildTools: ProprietaryBuildTools = ProprietaryBuildTools.DUMMY,
                      options: BuildOptions = BuildOptions()): BuildContextImpl {
      val projectHomeAsString = FileUtilRt.toSystemIndependentName(projectHome.toString())
      val windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeAsString)
      val linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeAsString)
      val macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeAsString)
      val compilationContext = CompilationContextImpl.create(
        communityHome = communityHome,
        projectHome = projectHome,
        buildOutputRootEvaluator = createBuildOutputRootEvaluator(projectHome, productProperties, options),
        options = options
      )
      return BuildContextImpl(compilationContext = compilationContext,
                              productProperties = productProperties,
                              windowsDistributionCustomizer = windowsDistributionCustomizer,
                              linuxDistributionCustomizer = linuxDistributionCustomizer,
                              macDistributionCustomizer = macDistributionCustomizer,
                              proprietaryBuildTools = proprietaryBuildTools,
                              distFiles = ConcurrentLinkedQueue())
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

  override fun addDistFile(file: Map.Entry<Path, String>) {
    messages.debug("$file requested to be added to app resources")
    distFiles.add(file)
  }

  override fun getDistFiles(): Collection<Map.Entry<Path, String>> {
    return java.util.List.copyOf(distFiles)
  }

  override fun findApplicationInfoModule(): JpsModule {
    return findRequiredModule(productProperties.applicationInfoModule)
  }

  override val options: BuildOptions
    get() = compilationContext.options
  @Suppress("SSBasedInspection")
  override val messages: BuildMessages
    get() = compilationContext.messages
  override val dependenciesProperties: DependenciesProperties
    get() = compilationContext.dependenciesProperties
  override val paths: BuildPaths
    get() = compilationContext.paths
  override val bundledRuntime: BundledRuntime
    get() = compilationContext.bundledRuntime
  override val project: JpsProject
    get() = compilationContext.project
  override val projectModel: JpsModel
    get() = compilationContext.projectModel
  override val compilationData: JpsCompilationData
    get() = compilationContext.compilationData
  override val stableJavaExecutable: Path
    get() = compilationContext.stableJavaExecutable
  override val stableJdkHome: Path
    get() = compilationContext.stableJdkHome
  override val projectOutputDirectory: Path
    get() = compilationContext.projectOutputDirectory

  override fun findRequiredModule(name: String): JpsModule {
    return compilationContext.findRequiredModule(name)
  }

  override fun findModule(name: String): JpsModule? {
    return compilationContext.findModule(name)
  }

  override fun getOldModuleName(newName: String): String? {
    return compilationContext.getOldModuleName(newName)
  }

  override fun getModuleOutputDir(module: JpsModule): Path {
    return compilationContext.getModuleOutputDir(module)
  }

  override fun getModuleTestsOutputPath(module: JpsModule): String {
    return compilationContext.getModuleTestsOutputPath(module)
  }

  override fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    return compilationContext.getModuleRuntimeClasspath(module, forTests)
  }

  override fun notifyArtifactBuilt(artifactPath: Path) {
    compilationContext.notifyArtifactWasBuilt(artifactPath)
  }

  override fun notifyArtifactWasBuilt(artifactPath: Path) {
    compilationContext.notifyArtifactWasBuilt(artifactPath)
  }

  override fun findFileInModuleSources(moduleName: String, relativePath: String): Path? {
    for (info in getSourceRootsWithPrefixes(findRequiredModule(moduleName))) {
      if (relativePath.startsWith(info.second)) {
        val result = info.first.resolve(Strings.trimStart(Strings.trimStart(relativePath, info.second), "/"))
        if (Files.exists(result)) {
          return result
        }
      }
    }
    return null
  }

  override fun signFiles(files: List<Path>, options: Map<String, String>) {
    if (proprietaryBuildTools.signTool == null) {
      Span.current().addEvent("files won't be signed", Attributes.of(
        AttributeKey.stringArrayKey("files"), files.map(Path::toString),
        AttributeKey.stringKey("reason"), "sign tool isn't defined")
      )
    }
    else {
      proprietaryBuildTools.signTool.signFiles(files, this, options)
    }
  }

  override fun executeStep(stepMessage: String, stepId: String, step: Runnable): Boolean {
    if (options.buildStepsToSkip.contains(stepId)) {
      Span.current().addEvent("skip step", Attributes.of(AttributeKey.stringKey("name"), stepMessage))
    }
    else {
      messages.block(stepMessage, step::run)
    }
    return true
  }

  override fun shouldBuildDistributions(): Boolean {
    return options.targetOs.lowercase() != BuildOptions.OS_NONE
  }

  override fun shouldBuildDistributionForOS(os: OsFamily, arch: JvmArchitecture): Boolean {
    return shouldBuildDistributions()
           && listOf(BuildOptions.OS_ALL, os.osId).contains(options.targetOs.lowercase())
           && (options.targetArch == null || options.targetArch == arch)
  }

  override fun createCopyForProduct(productProperties: ProductProperties, projectHomeForCustomizers: Path): BuildContext {
    val projectHomeForCustomizersAsString = FileUtilRt.toSystemIndependentName(projectHomeForCustomizers.toString())

    /**
     * FIXME compiled classes are assumed to be already fetched in the FIXME from [CompilationContextImpl.prepareForBuild], please change them together
     */
    val options = BuildOptions()
    options.useCompiledClassesFromProjectOutput = true
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
      distFiles = ConcurrentLinkedQueue()
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

  override fun getAdditionalJvmArguments(os: OsFamily): List<String> {
    val jvmArgs: MutableList<String> = ArrayList()
    val classLoader = productProperties.classLoader
    if (classLoader != null) {
      jvmArgs.add("-Djava.system.class.loader=$classLoader")
    }
    jvmArgs.add("-Didea.vendor.name=${applicationInfo.shortCompanyName}")
    jvmArgs.add("-Didea.paths.selector=$systemSelector")
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

private fun readSnapshotBuildNumber(communityHome: BuildDependenciesCommunityRoot): String {
  return Files.readString(communityHome.communityRoot.resolve("build.txt")).trim { it <= ' ' }
}

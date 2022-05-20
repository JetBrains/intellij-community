// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Strings
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import org.codehaus.groovy.runtime.StringGroovyMethods
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.ProprietaryBuildTools.Companion.DUMMY
import org.jetbrains.intellij.build.TracerProviderManager.flush
import org.jetbrains.intellij.build.impl.CompilationContextImpl.Companion.create
import org.jetbrains.intellij.build.projector.configure
import org.jetbrains.jps.model.JpsGlobal
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
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiFunction
import java.util.stream.Collectors

class BuildContextImpl : BuildContext {
  override val fullBuildNumber: String
    get() = "${applicationInfo.productCode}-$buildNumber"

  override val systemSelector: String
    get() = productProperties.getSystemSelector(applicationInfo, buildNumber)


  override val productProperties: ProductProperties
  override val windowsDistributionCustomizer: WindowsDistributionCustomizer?
  override val linuxDistributionCustomizer: LinuxDistributionCustomizer?
  override val macDistributionCustomizer: MacDistributionCustomizer?
  override val proprietaryBuildTools: ProprietaryBuildTools
  override val buildNumber: String
  override var xBootClassPathJarNames: List<String>
  override var bootClassPathJarNames: List<String>
  override var classpathCustomizer: (MutableSet<String>) -> Unit = {}

  override val applicationInfo: ApplicationInfoProperties
  private val global: JpsGlobal
  private val compilationContext: CompilationContextImpl
  private val distFiles: ConcurrentLinkedQueue<Map.Entry<Path, String>>
  private var builtinModulesData: BuiltinModulesFileData? = null

  private constructor(compilationContext: CompilationContextImpl,
                      productProperties: ProductProperties,
                      windowsDistributionCustomizer: WindowsDistributionCustomizer?,
                      linuxDistributionCustomizer: LinuxDistributionCustomizer?,
                      macDistributionCustomizer: MacDistributionCustomizer?,
                      proprietaryBuildTools: ProprietaryBuildTools?,
                      distFiles: ConcurrentLinkedQueue<Map.Entry<Path, String>>) {
    this.compilationContext = compilationContext
    global = compilationContext.global
    this.productProperties = productProperties
    this.distFiles = distFiles
    this.proprietaryBuildTools = proprietaryBuildTools ?: DUMMY
    this.windowsDistributionCustomizer = windowsDistributionCustomizer
    this.linuxDistributionCustomizer = linuxDistributionCustomizer
    this.macDistributionCustomizer = macDistributionCustomizer
    val number = options.buildNumber
    buildNumber = number ?: readSnapshotBuildNumber(paths.communityHomeDir)
    xBootClassPathJarNames = productProperties.xBootClassPathJarNames
    bootClassPathJarNames = listOf("util.jar", "util_rt.jar")
    applicationInfo = ApplicationInfoPropertiesImpl(project, productProperties, options, messages).patch(this)
    if (productProperties.productCode == null) {
      productProperties.productCode = applicationInfo.productCode
    }
    if (systemSelector.contains(" ")) {
      messages.error("System selector must not contain spaces: $systemSelector")
    }
    options.buildStepsToSkip.addAll(productProperties.incompatibleBuildSteps)
    if (!options.buildStepsToSkip.isEmpty()) {
      messages.info("Build steps to be skipped: ${options.buildStepsToSkip.joinToString()}")
    }
    configure(productProperties)
  }

  private constructor(parent: BuildContextImpl,
                      messages: BuildMessages,
                      distFiles: ConcurrentLinkedQueue<Map.Entry<Path, String>>) {
    compilationContext = parent.compilationContext.cloneForContext(messages)
    this.distFiles = distFiles
    global = compilationContext.global
    productProperties = parent.productProperties
    proprietaryBuildTools = parent.proprietaryBuildTools
    windowsDistributionCustomizer = parent.windowsDistributionCustomizer
    linuxDistributionCustomizer = parent.linuxDistributionCustomizer
    macDistributionCustomizer = parent.macDistributionCustomizer
    buildNumber = parent.buildNumber
    xBootClassPathJarNames = parent.xBootClassPathJarNames
    bootClassPathJarNames = parent.bootClassPathJarNames
    applicationInfo = parent.applicationInfo
    builtinModulesData = parent.builtinModulesData
  }

  companion object {
    @JvmStatic
    fun createContext(communityHome: Path,
                      projectHome: Path,
                      productProperties: ProductProperties,
                      proprietaryBuildTools: ProprietaryBuildTools?,
                      options: BuildOptions): BuildContext {
      return create(communityHome = communityHome,
                    projectHome = projectHome,
                    productProperties = productProperties,
                    proprietaryBuildTools = proprietaryBuildTools,
                    options = options)
    }

    @JvmStatic
    fun createContext(communityHome: Path,
                      projectHome: Path,
                      productProperties: ProductProperties,
                      proprietaryBuildTools: ProprietaryBuildTools?): BuildContext {
      return createContext(communityHome = communityHome,
                           projectHome = projectHome,
                           productProperties = productProperties,
                           proprietaryBuildTools = proprietaryBuildTools,
                           options = BuildOptions())
    }

    @JvmStatic
    fun createContext(communityHome: Path, projectHome: Path, productProperties: ProductProperties): BuildContext {
      return createContext(communityHome = communityHome,
                           projectHome = projectHome,
                           productProperties = productProperties,
                           proprietaryBuildTools = DUMMY,
                           options = BuildOptions())
    }

    @JvmStatic
    fun create(communityHome: Path,
               projectHome: Path,
               productProperties: ProductProperties,
               proprietaryBuildTools: ProprietaryBuildTools?,
               options: BuildOptions): BuildContextImpl {
      val projectHomeAsString = FileUtilRt.toSystemIndependentName(projectHome.toString())
      val windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeAsString)
      val linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeAsString)
      val macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeAsString)
      val compilationContext = create(communityHome = communityHome,
                                      projectHome = projectHome,
                                      buildOutputRootEvaluator = createBuildOutputRootEvaluator(projectHomeAsString,
                                                                                                productProperties, options),
                                      options = options)
      return BuildContextImpl(compilationContext, productProperties, windowsDistributionCustomizer, linuxDistributionCustomizer,
                              macDistributionCustomizer, proprietaryBuildTools, ConcurrentLinkedQueue())
    }

    @JvmStatic
    fun readSnapshotBuildNumber(communityHome: Path): String {
      return Files.readString(communityHome.resolve("build.txt")).trim { it <= ' ' }
    }
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
      if (relativePath.startsWith(info.getSecond()!!)) {
        val result = info.getFirst().resolve(Strings.trimStart(Strings.trimStart(relativePath, info.getSecond()!!), "/"))
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

  override fun executeStep(spanBuilder: SpanBuilder, stepId: String, step: Runnable) {
    if (options.buildStepsToSkip.contains(stepId)) {
      spanBuilder.startSpan().addEvent("skip").end()
      return
    }
    val span = spanBuilder.startSpan()
    val scope = span.makeCurrent()
    // we cannot flush tracing after "throw e" as we have to end the current span before that
    var success = false
    try {
      step.run()
      success = true
    }
    catch (e: Throwable) {
      span.recordException(e)
      span.setStatus(StatusCode.ERROR, e.message!!)
      throw e
    }
    finally {
      try {
        scope.close()
      }
      finally {
        span.end()
      }
      if (!success) {
        // print all pending spans - after current span
        flush()
      }
    }
  }

  override fun shouldBuildDistributions(): Boolean {
    return options.targetOs!!.lowercase(Locale.getDefault()) != BuildOptions.OS_NONE
  }

  override fun shouldBuildDistributionForOS(os: String): Boolean {
    return shouldBuildDistributions() && listOf(BuildOptions.OS_ALL, os)
      .contains(options.targetOs!!.lowercase(Locale.getDefault()))
  }

  override fun forkForParallelTask(taskName: String): BuildContext {
    return BuildContextImpl(this, messages.forkForParallelTask(taskName), distFiles)
  }

  override fun createCopyForProduct(productProperties: ProductProperties, projectHomeForCustomizers: Path): BuildContext {
    val projectHomeForCustomizersAsString = FileUtilRt.toSystemIndependentName(projectHomeForCustomizers.toString())
    val windowsDistributionCustomizer = productProperties.createWindowsCustomizer(projectHomeForCustomizersAsString)
    val linuxDistributionCustomizer = productProperties.createLinuxCustomizer(projectHomeForCustomizersAsString)
    val macDistributionCustomizer = productProperties.createMacCustomizer(projectHomeForCustomizersAsString)

    /**
     * FIXME compiled classes are assumed to be already fetched in the FIXME from [CompilationContextImpl.prepareForBuild], please change them together
     */
    val options = BuildOptions()
    options.useCompiledClassesFromProjectOutput = true
    val compilationContextCopy = compilationContext.createCopy(messages, options, createBuildOutputRootEvaluator(
      paths.projectHome, productProperties, options))
    val copy = BuildContextImpl(compilationContextCopy, productProperties, windowsDistributionCustomizer, linuxDistributionCustomizer,
                                macDistributionCustomizer, proprietaryBuildTools, ConcurrentLinkedQueue())
    copy.paths.artifactDir = paths.artifactDir.resolve(productProperties.productCode!!)
    copy.paths.artifacts = "${paths.artifacts}/${productProperties.productCode}"
    copy.compilationContext.prepareForBuild()
    return copy
  }

  override fun includeBreakGenLibraries(): Boolean {
    return isJavaSupportedInProduct
  }

  private val isJavaSupportedInProduct: Boolean
    get() = productProperties.productLayout.bundledPluginModules.contains("intellij.java.plugin")

  override fun patchInspectScript(path: Path) {
    //todo[nik] use placeholder in inspect.sh/inspect.bat file instead
    Files.writeString(path, StringGroovyMethods.replaceAll(Files.readString(path), " inspect ",
                                                           " " + productProperties.inspectCommandName + " "))
  }

  override fun getAdditionalJvmArguments(): List<String> {
    val jvmArgs: MutableList<String> = ArrayList()
    val classLoader = productProperties.classLoader
    if (classLoader != null) {
      jvmArgs.add("-Djava.system.class.loader=$classLoader")
      if (classLoader == "com.intellij.util.lang.PathClassLoader") {
        jvmArgs.add("-Didea.strict.classpath=true")
      }
    }
    jvmArgs.add("-Didea.vendor.name=" + applicationInfo.shortCompanyName)
    jvmArgs.add("-Didea.paths.selector=$systemSelector")
    if (productProperties.platformPrefix != null) {
      jvmArgs.add("-Didea.platform.prefix=" + productProperties.platformPrefix)
    }
    jvmArgs.addAll(productProperties.additionalIdeJvmArguments)
    if (productProperties.toolsJarRequired) {
      jvmArgs.add("-Didea.jre.check=true")
    }
    if (productProperties.useSplash) {
      @Suppress("SpellCheckingInspection")
      jvmArgs.add("-Dsplash=true")
    }
    jvmArgs.addAll(getCommandLineArgumentsForOpenPackages(this))
    return jvmArgs
  }

  override fun getBuiltinModule(): BuiltinModulesFileData? {
    if (options.buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
      return null
    }
    return builtinModulesData ?: throw IllegalStateException("builtinModulesData is not set. " +
                                                             "Make sure `BuildTasksImpl.buildProvidedModuleList` was called before")
  }

  fun setBuiltinModules(data: BuiltinModulesFileData?) {
    check(builtinModulesData == null) { "builtinModulesData was already set" }
    builtinModulesData = data
  }
}

private fun createBuildOutputRootEvaluator(projectHome: String,
                                           productProperties: ProductProperties,
                                           buildOptions: BuildOptions): BiFunction<JpsProject, BuildMessages, String> {
  return BiFunction { project: JpsProject?, messages: BuildMessages? ->
    val applicationInfo: ApplicationInfoProperties = ApplicationInfoPropertiesImpl(project, productProperties, buildOptions, messages)
    projectHome + "/out/" + productProperties.getOutputDirectoryName(applicationInfo)
  }
}

private fun getSourceRootsWithPrefixes(module: JpsModule): List<Pair<Path, String?>> {
  return module.sourceRoots.stream().filter { root: JpsModuleSourceRoot ->
    JavaModuleSourceRootTypes.PRODUCTION.contains(root.rootType)
  }.map { moduleSourceRoot: JpsModuleSourceRoot ->
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
    Pair(Path.of(JpsPathUtil.urlToPath(moduleSourceRoot.url)), Strings.trimStart(prefix, "/"))
  }.collect(Collectors.toList())
}
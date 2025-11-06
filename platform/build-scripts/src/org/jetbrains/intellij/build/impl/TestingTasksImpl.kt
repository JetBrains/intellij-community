// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.TestCaseLoader
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.util.io.awaitExit
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildCancellationException
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.LinuxLibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.TestingOptions
import org.jetbrains.intellij.build.TestingTasks
import org.jetbrains.intellij.build.causal.CausalProfilingOptions
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import org.jetbrains.intellij.build.impl.coverage.Coverage
import org.jetbrains.intellij.build.impl.coverage.CoverageImpl
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.block
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.java.ModulePathSplitter
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.PrintStream
import java.lang.reflect.Modifier
import java.nio.charset.Charset
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.random.Random

private const val NO_TESTS_ERROR = 42

internal class TestingTasksImpl(context: CompilationContext, private val options: TestingOptions) : TestingTasks {
  private val context: CompilationContext = if (options.useArchivedCompiledClasses) context.asArchived else context

  override val coverage: Coverage by lazy {
    CoverageImpl(
      context = this.context,
      coveredModuleNames = runConfigurations
                             .map { it.moduleName }
                             .takeIf { it.any() }
                           ?: listOfNotNull(options.mainModule),
      coveredClasses = requireNotNull(options.coveredClassesPatterns) {
        "Test coverage is enabled but the classes pattern is not specified"
      }.splitToSequence(';').map(::Regex).toList(),
    )
  }

  private fun loadRunConfigurations(name: String): List<JUnitRunConfigurationProperties> {
    val projectHome = context.paths.projectHome
    val file = RunConfigurationProperties.findRunConfiguration(projectHome, name)
    val configuration = RunConfigurationProperties.getConfiguration(file)
    return when (val type = RunConfigurationProperties.getConfigurationType(configuration)) {
      JUnitRunConfigurationProperties.TYPE -> {
        listOf(JUnitRunConfigurationProperties.loadRunConfiguration(file))
      }
      CompoundRunConfigurationProperties.TYPE -> {
        val runConfiguration = CompoundRunConfigurationProperties.loadRunConfiguration(file)
        runConfiguration.toRun.flatMap(::loadRunConfigurations)
      }
      else -> {
        throw RuntimeException("Unsupported run configuration type '${type}' in run configuration '${name}' of project '${projectHome}'")
      }
    }
  }

  private val runConfigurations: List<JUnitRunConfigurationProperties> by lazy {
    options.testConfigurations
      ?.splitToSequence(';')
      ?.filter(String::isNotEmpty)
      ?.flatMap(::loadRunConfigurations)
      ?.toList() ?: emptyList()
  }

  @Deprecated("the `defaultMainModule` should be passed via `TestingOptions#mainModule`")
  override suspend fun runTests(
    additionalJvmOptions: List<String>,
    additionalSystemProperties: Map<String, String>,
    defaultMainModule: String?,
    rootExcludeCondition: ((Path) -> Boolean)?,
  ) {
    require(defaultMainModule == null) {
      "The `defaultMainModule` parameter is deprecated, please use `TestingOptions#mainModule` instead."
    }
    runTests(additionalJvmOptions, additionalSystemProperties, rootExcludeCondition)
  }

  override suspend fun runTests(additionalJvmOptions: List<String>, additionalSystemProperties: Map<String, String>, rootExcludeCondition: ((Path) -> Boolean)?) {
    if (options.redirectStdOutToFile && !TeamCityHelper.isUnderTeamCity) {
      context.messages.warning("'${TestingOptions.REDIRECT_STDOUT_TO_FILE}' can be set only for a TeamCity build, ignored.")
    }
    if (TeamCityHelper.isUnderTeamCity && options.redirectStdOutToFile) {
      redirectStdOutToFile {
        runTestsImpl(additionalJvmOptions, additionalSystemProperties, rootExcludeCondition)
      }
    }
    else {
      runTestsImpl(additionalJvmOptions, additionalSystemProperties, rootExcludeCondition)
    }
  }

  /**
   * See [TestingOptions.redirectStdOutToFile]
   */
  private suspend fun redirectStdOutToFile(runTests: suspend () -> Unit) {
    val outputFile = context.paths.tempDir.resolve("testStdOut.txt")
    context.messages.startWritingFileToBuildLog(outputFile.absolutePathString())
    val outputStream = System.out
    PrintStream(outputFile.outputStream()).use {
      System.setOut(it)
      try {
        runTests()
      }
      finally {
        System.setOut(outputStream)
        context.messages.artifactBuilt(outputFile.absolutePathString())
      }
    }
  }

  private suspend fun runTestsImpl(
    additionalJvmOptions: List<String>,
    additionalSystemProperties: Map<String, String>,
    rootExcludeCondition: ((Path) -> Boolean)?,
  ) {
    if (options.enableCoverage && options.isPerformanceTestsOnly) {
      context.messages.buildStatus("Skipping performance testing with Coverage, {build.status.text}")
      return
    }
    if (options.isTestDiscoveryEnabled && options.isPerformanceTestsOnly) {
      context.messages.buildStatus("Skipping performance testing with Test Discovery, {build.status.text}")
      return
    }

    val mainModule = options.mainModule
    checkOptions(mainModule)

    if (options.validateMainModule) {
      checkNotNull(mainModule)
      val withModuleMismatch = runConfigurations.filter { it.moduleName != mainModule }
      if (withModuleMismatch.isNotEmpty()) {
        val errorMessage = withModuleMismatch.joinToString(
          prefix = "Run configuration module mismatch, expected '$mainModule' (set in option 'intellij.build.test.main.module'), actual:\n",
          separator = "\n",
        ) { "  * Run configuration: '${it.name}', module: '${it.moduleName}'" }
        context.messages.logErrorAndThrow(errorMessage)
      }
    }

    val systemProperties = LinkedHashMap<String, String>(additionalSystemProperties)
    try {
      if (runConfigurations.any { it.buildProject }) {
        context.messages.info(
          "Building the entire project as requested by run configurations: " +
          runConfigurations.filter { it.buildProject }.map { it.name }
        )
        context.compileModules(moduleNames = null, includingTestsInModules = null)
      }
      else if (runConfigurations.any()) {
        context.compileModules(
          moduleNames = listOf("intellij.tools.testsBootstrap"),
          includingTestsInModules = listOf("intellij.platform.buildScripts") + runConfigurations.map { it.moduleName },
        )
      }
      else {
        context.compileModules(
          moduleNames = listOf("intellij.tools.testsBootstrap"),
          includingTestsInModules = listOfNotNull(mainModule, "intellij.platform.buildScripts"),
        )
      }
      val runtimeModuleRepository = context.getOriginalModuleRepository()
      systemProperties.put("intellij.platform.runtime.repository.path", runtimeModuleRepository.repositoryPath.pathString)
    }
    catch (e: Exception) {
      if (options.isCancelBuildOnTestPreparationFailure) {
        throw BuildCancellationException(e)
      }
      throw e
    }

    val remoteDebugJvmOptions = System.getProperty("teamcity.remote-debug.jvm.options")
    if (remoteDebugJvmOptions != null) {
      debugTests(remoteDebugJvmOptions, additionalJvmOptions, checkNotNull(mainModule) {
        "Main module is not specified"
      })
    }
    else {
      val effectiveAdditionalJvmOptions = additionalJvmOptions.toMutableList()
      if (options.isTestDiscoveryEnabled) {
        loadTestDiscovery(effectiveAdditionalJvmOptions, systemProperties)
      }
      if (options.enableCoverage) {
        coverage.enable(jvmOptions = effectiveAdditionalJvmOptions, systemProperties = systemProperties)
      }
      if (runConfigurations.none()) {
        runTestsFromGroupsAndPatterns(
          additionalJvmOptions = effectiveAdditionalJvmOptions,
          mainModule = checkNotNull(mainModule) {
            "Main module is not specified"
          },
          rootExcludeCondition = rootExcludeCondition,
          systemProperties = systemProperties
        )
      }
      else {
        runTestsFromRunConfigurations(effectiveAdditionalJvmOptions, runConfigurations, systemProperties)
      }
      if (options.isTestDiscoveryEnabled) {
        publishTestDiscovery(context.messages, testDiscoveryTraceFilePath)
      }
      if (options.enableCoverage) {
        coverage.generateReport()
      }
    }
  }

  private fun checkOptions(mainModule: String?) {
    if (options.testConfigurations != null) {
      val testConfigurationsOptionName = "intellij.build.test.configurations"
      if (options.testPatterns != null) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.patterns")
      }
      if (options.testGroups != TestingOptions.ALL_EXCLUDE_DEFINED_GROUP) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.groups")
      }
      if (mainModule != null && !options.validateMainModule) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.main.module")
      }
    }
    else if (options.testPatterns != null && options.testGroups != TestingOptions.ALL_EXCLUDE_DEFINED_GROUP) {
      warnOptionIgnored("intellij.build.test.patterns", "intellij.build.test.groups")
    }
    if (options.batchTestIncludes != null && !isRunningInBatchMode) {
      context.messages.warning(
        "'intellij.build.test.batchTest.includes' option will be ignored as other tests matching options are specified."
      )
    }

    if (options.validateMainModule && mainModule.isNullOrEmpty()) {
      context.messages.logErrorAndThrow("'intellij.build.test.main.module.validate' option requires 'intellij.build.test.main.module' to be set")
    }
  }

  private fun warnOptionIgnored(specifiedOption: String, ignoredOption: String) {
    context.messages.warning("'${specifiedOption}' option is specified, so '${ignoredOption}' will be ignored.")
  }

  private suspend fun runTestsFromRunConfigurations(
    additionalJvmOptions: List<String>,
    runConfigurations: List<JUnitRunConfigurationProperties>,
    systemProperties: MutableMap<String, String>,
  ) {
    for (configuration in runConfigurations) {
      spanBuilder("run '${configuration.name}' run configuration").use {
        runTestsFromRunConfiguration(runConfigurationProperties = configuration, additionalJvmOptions = additionalJvmOptions, systemProperties = systemProperties)
      }
    }
  }

  private suspend fun runTestsFromRunConfiguration(
    runConfigurationProperties: JUnitRunConfigurationProperties,
    additionalJvmOptions: List<String>,
    systemProperties: Map<String, String>,
  ) {
    if (runConfigurationProperties.testSearchScope != JUnitRunConfigurationProperties.TestSearchScope.MODULE_WITH_DEPENDENCIES) {
      context.messages.warning(
        "Run configuration '${runConfigurationProperties.name}' uses test search scope '${runConfigurationProperties.testSearchScope.serialized}', " +
        "while only '${JUnitRunConfigurationProperties.TestSearchScope.MODULE_WITH_DEPENDENCIES.serialized}' is supported. Scope will be ignored"
      )
    }
    try {
      runTestsProcess(
        mainModule = runConfigurationProperties.moduleName,
        testGroups = null,
        testPatterns = runConfigurationProperties.testClassPatterns.joinToString(separator = ";"),
        jvmArgs = removeStandardJvmOptions(runConfigurationProperties.vmParameters) + additionalJvmOptions
                  + "-Dintellij.build.run.configuration.name=${runConfigurationProperties.name}",
        systemProperties = systemProperties,
        envVariables = runConfigurationProperties.envVariables,
        remoteDebugging = false,
      )
    }
    catch (e: NoTestsFound) {
      throw RuntimeException("No tests were found in the configuration '${runConfigurationProperties.name}'").apply {
        addSuppressed(e)
      }
    }
  }

  private suspend fun runTestsFromGroupsAndPatterns(
    additionalJvmOptions: List<String>,
    mainModule: String,
    rootExcludeCondition: ((Path) -> Boolean)?,
    systemProperties: MutableMap<String, String>,
  ) {
    if (rootExcludeCondition != null) {
      val excludedRootPaths = ArrayList<Path>(context.project.modules.size * 2)
      for (module in context.project.modules) {
        val contentRoots = module.contentRootsList.urls
        if (!contentRoots.isEmpty() && rootExcludeCondition(Path.of(JpsPathUtil.urlToPath(contentRoots.first())))) {
          excludedRootPaths.addAll(context.getModuleOutputRoots(module))
          excludedRootPaths.addAll(context.getModuleOutputRoots(module, forTests = true))
        }
      }
      val excludedRoots = replaceWithArchivedIfNeededLP(excludedRootPaths).filter(Files::exists).map(Path::toString)

      val excludedRootsFile = context.paths.tempDir.resolve("excluded.classpath")
      Files.createDirectories(excludedRootsFile.parent)
      Files.writeString(excludedRootsFile, excludedRoots.joinToString(separator = "\n"))
      systemProperties.put("exclude.tests.roots.file", excludedRootsFile.toString())
    }

    try {
      runTestsProcess(
        mainModule = mainModule,
        testGroups = options.testGroups,
        testPatterns = options.testPatterns,
        jvmArgs = additionalJvmOptions,
        systemProperties = systemProperties,
        remoteDebugging = false
      )
    }
    catch (e: NoTestsFound) {
      val msg = buildString {
        append("No tests were found in '$mainModule' classpath ")
        if (options.testPatterns != null) {
          append("with test patterns '${options.testPatterns}'")
        }
        else {
          append("for test groups '${options.testGroups}'")
        }
      }
      throw RuntimeException(msg).apply {
        addSuppressed(e)
      }
    }
  }

  private fun loadTestDiscovery(additionalJvmOptions: MutableList<String>, systemProperties: MutableMap<String, String>) {
    val testDiscovery = "intellij-test-discovery"
    val library = context.projectModel.project.libraryCollection.findLibrary(testDiscovery)
                  ?: throw RuntimeException("Can't find the $testDiscovery library, but test discovery capturing enabled.")

    val agentJar = library.getPaths(JpsOrderRootType.COMPILED)
                     .firstOrNull {
                       val name = it.fileName.toString()
                       name.startsWith("intellij-test-discovery") && name.endsWith(".jar")
                     } ?: throw RuntimeException("Can't find the agent in $testDiscovery library, but test discovery capturing enabled.")

    additionalJvmOptions += "-javaagent:${agentJar}"
    val excludeRoots = context.projectModel.global.libraryCollection.getLibraries(JpsJavaSdkType.INSTANCE)
      .mapTo(LinkedHashSet()) { FileUtilRt.toSystemDependentName(it.properties.homePath) }
    excludeRoots += context.paths.buildOutputDir.toString()
    excludeRoots += context.paths.projectHome.resolve("out").toString()

    systemProperties["test.discovery.listener"] = "com.intellij.TestDiscoveryBasicListener"
    systemProperties["test.discovery.data.listener"] = "com.intellij.rt.coverage.data.SingleTrFileDiscoveryProtocolDataListener"
    systemProperties["org.jetbrains.instrumentation.trace.file"] = testDiscoveryTraceFilePath

    options.testDiscoveryIncludePatterns?.let { systemProperties["test.discovery.include.class.patterns"] = it }
    options.testDiscoveryExcludePatterns?.let { systemProperties["test.discovery.exclude.class.patterns"] = it }

    systemProperties.put("test.discovery.excluded.roots", excludeRoots.joinToString(separator = ";"))
  }

  private val testDiscoveryTraceFilePath: String
    get() = options.testDiscoveryTraceFilePath ?: context.paths.projectHome.resolve("intellij-tracing/td.tr").toString()

  private suspend fun debugTests(
    remoteDebugJvmOptions: String,
    additionalJvmOptions: List<String>,
    mainModule: String,
  ) {
    val testConfigurationType = System.getProperty("teamcity.remote-debug.type")
    if (testConfigurationType != "junit") {
      context.messages.logErrorAndThrow(
        "Remote debugging is supported for junit run configurations only, but 'teamcity.remote-debug.type' is $testConfigurationType"
      )
    }
    val testObject = System.getProperty("teamcity.remote-debug.junit.type")
    val junitClass = System.getProperty("teamcity.remote-debug.junit.class")
    if (testObject != "class") {
      val message = "Remote debugging supports debugging all test methods in a class for now, debugging isn't supported for '${testObject}'"
      if (testObject == "method") {
        context.messages.warning(message)
        context.messages.warning("Launching all test methods in the class $junitClass")
      }
      else {
        context.messages.logErrorAndThrow(message)
      }
    }
    if (junitClass == null) {
      context.messages.logErrorAndThrow("Remote debugging supports debugging all test methods in a class for now, but target class isn't specified")
    }
    if (options.testPatterns != null) {
      context.messages.warning("'intellij.build.test.patterns' option is ignored while debugging via TeamCity plugin")
    }
    if (options.testConfigurations != null) {
      context.messages.warning("'intellij.build.test.configurations' option is ignored while debugging via TeamCity plugin")
    }
    runTestsProcess(
      mainModule = mainModule,
      testGroups = null,
      testPatterns = junitClass,
      jvmArgs = removeStandardJvmOptions(StringUtilRt.splitHonorQuotes(remoteDebugJvmOptions, ' ')) + additionalJvmOptions,
      systemProperties = emptyMap(),
      remoteDebugging = true
    )
  }

  private suspend fun runTestsProcess(
    mainModule: String,
    testGroups: String?,
    testPatterns: String?,
    jvmArgs: List<String>,
    systemProperties: Map<String, String>,
    envVariables: Map<String, String> = emptyMap(),
    remoteDebugging: Boolean,
  ) {
    val useKotlinK2 = !System.getProperty("idea.kotlin.plugin.use.k1", "false").toBoolean() ||
                      jvmArgs.contains("-Didea.kotlin.plugin.use.k1=false")
    val mainJpsModule = context.findRequiredModule(mainModule)
    val testRoots = JpsJavaExtensionService.dependencies(mainJpsModule).recursively()
      .withoutSdk()  // if the project requires different SDKs, they all shouldn't be added to the test classpath
      .includedIn(JpsJavaClasspathKind.runtime(true))
      .classes()
      .roots
      .filterTo(mutableListOf()) { useKotlinK2 || it.name != "kotlin.plugin.k2" }

    if (isBootstrapSuiteDefault && !isRunningInBatchMode) {
      //module with "com.intellij.TestAll" which output should be found in `testClasspath + modulePath`
      val testFrameworkCoreModule = context.findRequiredModule("intellij.platform.testFramework.core")
      val testFrameworkCoreModuleOutputRoots = context
        .getModuleOutputRoots(testFrameworkCoreModule)
        .map(Path::toFile)
      for (testFrameworkOutput in testFrameworkCoreModuleOutputRoots) {
        if (!testRoots.contains(testFrameworkOutput)) {
          testRoots.addAll(context.getModuleRuntimeClasspath(testFrameworkCoreModule, false).map(::File))
        }
      }
    }

    val testClasspath: List<String>
    val modulePath: List<String>?

    val moduleInfoFile = JpsJavaExtensionService.getInstance().getJavaModuleIndex(context.project).getModuleInfoFile(mainJpsModule, true)
    val toExistingAbsolutePathConverter: (File) -> String? = { if (it.exists()) it.absolutePath else null }
    if (moduleInfoFile != null) {
      val outputDir = ModuleBuildTarget(mainJpsModule, JavaModuleBuildTargetType.TEST).outputDir
      val pair = ModulePathSplitter().splitPath(moduleInfoFile, mutableSetOf(outputDir), HashSet(testRoots))
      modulePath = replaceWithArchivedIfNeededLF(pair.first.path.toList()).mapNotNull(toExistingAbsolutePathConverter)
      testClasspath = replaceWithArchivedIfNeededLF(pair.second.toList()).mapNotNull(toExistingAbsolutePathConverter)
    }
    else {
      modulePath = null
      testClasspath = replaceWithArchivedIfNeededLF(testRoots).mapNotNull(toExistingAbsolutePathConverter)
    }

    val devBuildServerSettings = DevBuildServerSettings.readDevBuildServerSettingsFromIntellijYaml(mainModule)
    val bootstrapClasspath = context.getModuleRuntimeClasspath(module = context.findRequiredModule("intellij.tools.testsBootstrap"), forTests = false).toMutableList()
    val classpathFile = context.paths.tempDir.resolve("junit.classpath")
    Files.createDirectories(classpathFile.parent)
    // this is required to collect tests both on class and module paths
    Files.writeString(classpathFile, replaceWithArchivedIfNeededLF(testRoots).mapNotNull(toExistingAbsolutePathConverter).joinToString(separator = "\n"))
    @Suppress("NAME_SHADOWING")
    val systemProperties = systemProperties.toMutableMap()
    systemProperties.put("io.netty.allocator.type", "pooled")
    systemProperties.putIfAbsent("classpath.file", classpathFile.toString())
    testPatterns?.let { systemProperties.putIfAbsent("intellij.build.test.patterns", it) }
    testGroups?.let { systemProperties.putIfAbsent("intellij.build.test.groups", it) }
    systemProperties.putIfAbsent("intellij.build.test.sorter", System.getProperty("intellij.build.test.sorter"))
    systemProperties.putIfAbsent(TestingTasks.BOOTSTRAP_TESTCASES_PROPERTY, "com.intellij.AllTests")
    systemProperties.putIfAbsent(TestingOptions.PERFORMANCE_TESTS_ONLY_FLAG, options.isPerformanceTestsOnly.toString())
    val allJvmArgs = ArrayList(jvmArgs)
    prepareEnvForTestRun(allJvmArgs, systemProperties, bootstrapClasspath, remoteDebugging)
    val messages = context.messages
    if (isRunningInBatchMode) {
      messages.info("Running tests from $mainModule matched by '${options.batchTestIncludes}' pattern.")
    }
    else if (!testPatterns.isNullOrEmpty()) {
      messages.info("Starting tests from patterns '${testPatterns}' from classpath of module '${mainModule}'")
    }
    else {
      messages.info("Starting tests from groups '${testGroups}' from classpath of module '${mainModule}'")
    }
    if (options.bucketsCount > 1) {
      messages.info("Tests from bucket ${options.bucketIndex} of ${options.bucketsCount} will be executed")
    }
    spanBuilder("test classpath and runtime info").use {
      withContext(Dispatchers.IO) {
        val runtime = getRuntimeExecutablePath().toString()
        messages.info("Runtime: $runtime")
        runProcess(args = listOf(runtime, "-version"), inheritOut = true, inheritErrToOut = true)
      }

      messages.info("Runtime options: $allJvmArgs")
      messages.info("System properties: $systemProperties")

      if (devBuildServerSettings == null) {
        messages.info("Bootstrap classpath: $bootstrapClasspath")
      }

      messages.info("Tests classpath: $testClasspath")
      modulePath?.let { mp ->
        @Suppress("SpellCheckingInspection")
        messages.info("Tests modulepath: $mp")
      }
      if (!envVariables.isEmpty()) {
        messages.info("Environment variables: $envVariables")
      }
      devBuildServerSettings?.let {
        messages.info("Dev build server settings: $it")
      }
    }
    runJUnit5Engine(
      mainModule = mainModule,
      systemProperties = systemProperties,
      jvmArgs = allJvmArgs,
      envVariables = envVariables,
      bootstrapClasspath = bootstrapClasspath,
      modulePath = modulePath,
      testClasspath = testClasspath,
      devBuildServerSettings = devBuildServerSettings,
    )
    notifySnapshotBuilt(allJvmArgs)
  }

  private suspend fun replaceWithArchivedIfNeededLF(files: List<File>, context: CompilationContext = this.context): List<File> {
    return when (context) {
      is BuildContextImpl -> replaceWithArchivedIfNeededLF(files, context.compilationContext)
      is ArchivedCompilationContext -> context.replaceWithCompressedIfNeededLF(files)
      is BazelCompilationContext -> context.replaceWithCompressedIfNeededLF(files)
      else -> files
    }
  }

  private suspend fun replaceWithArchivedIfNeededLP(paths: List<Path>): List<Path> {
    if (context is ArchivedCompilationContext) {
      return context.replaceWithCompressedIfNeededLP(paths)
    }
    else {
      return paths
    }
  }

  private suspend fun getRuntimeExecutablePath(): Path {
    val runtimeDir: Path
    if (options.customRuntimePath != null) {
      runtimeDir = Path.of(checkNotNull(options.customRuntimePath))
      check(Files.isDirectory(runtimeDir)) {
        "Custom Jre path from system property '${TestingOptions.TEST_JRE_PROPERTY}' is missing: $runtimeDir"
      }
    }
    else {
      runtimeDir = context.bundledRuntime.getHomeForCurrentOsAndArch()
    }

    var java = runtimeDir.resolve(if (SystemInfoRt.isWindows) "bin/java.exe" else "bin/java")
    if (SystemInfoRt.isMac && Files.notExists(java)) {
      java = runtimeDir.resolve("Contents/Home/bin/java")
    }
    check(Files.exists(java)) { "java executable is missing: $java" }
    return java
  }

  private fun notifySnapshotBuilt(jvmArgs: List<String>) {
    val option = "-XX:HeapDumpPath="
    val file = Path.of(jvmArgs.first { it.startsWith(option) }.substring(option.length))
    if (Files.exists(file)) {
      context.notifyArtifactBuilt(file)
    }
  }

  override fun createSnapshotsDirectory(): Path {
    val snapshotsDir = context.paths.projectHome.resolve("out/snapshots")
    Files.createDirectories(snapshotsDir)
    return snapshotsDir
  }

  override suspend fun prepareEnvForTestRun(
    jvmArgs: MutableList<String>,
    systemProperties: MutableMap<String, String>,
    classPath: MutableList<String>,
    remoteDebugging: Boolean,
    cleanSystemDir: Boolean,
  ) {
    val snapshotsDir = createSnapshotsDirectory()
    // a heap dump file should not be overridden by the next test process run if any
    val hprofSnapshotFilePath = snapshotsDir.resolve("intellij-tests-oom-${System.currentTimeMillis()}.hprof").toString()
    jvmArgs.addAll(0, listOf("-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=${hprofSnapshotFilePath}"))

    val customMemoryOptions = options.jvmMemoryOptions?.trim()?.split(Regex("\\s+"))?.takeIf { it.isNotEmpty() }
    jvmArgs.addAll(
      index = 0,
      elements = generateVmOptions(
        isEAP = true,
        customVmMemoryOptions = if (customMemoryOptions == null) mapOf("-Xms" to "750m", "-Xmx" to "1024m") else emptyMap(),
        additionalVmOptions = customMemoryOptions ?: emptyList(),
        platformPrefix = options.platformPrefix,
      ),
    )

    val tempDir = System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir"))
    val ideaSystemPath = Path.of("$tempDir/system")
    if (cleanSystemDir) {
      spanBuilder("idea.system.path cleanup").use(Dispatchers.IO) {
        try {
          NioFiles.deleteRecursively(ideaSystemPath)
        }
        catch (e: AccessDeniedException) {
          if (SystemInfoRt.isWindows) {
            context.messages.reportBuildProblem("Cannot delete $ideaSystemPath: ${e.message}")
          }
          else {
            throw e
          }
        }
      }
    }
    @Suppress("SpellCheckingInspection")
    for ((k, v) in sequenceOf(
      "idea.platform.prefix" to options.platformPrefix,
      "idea.home.path" to context.paths.projectHome.toString(),
      "idea.config.path" to "$tempDir/config",
      "idea.system.path" to "$ideaSystemPath",
      BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY to "${context.classesOutputDirectory}",
      "idea.coverage.enabled.build" to System.getProperty("idea.coverage.enabled.build"),
      "teamcity.buildConfName" to System.getProperty("teamcity.buildConfName"),
      "java.io.tmpdir" to tempDir,
      "teamcity.build.tempDir" to tempDir,
      "teamcity.tests.recentlyFailedTests.file" to System.getProperty("teamcity.tests.recentlyFailedTests.file"),
      "teamcity.build.branch" to System.getProperty(BuildOptions.TEAMCITY_BUILD_BRANCH),
      "teamcity.build.branch.is_default" to System.getProperty(BuildOptions.TEAMCITY_BUILD_BRANCH_IS_DEFAULT),
      "jna.nosys" to "true",
      "javax.xml.parsers.SAXParserFactory" to "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
      "file.encoding" to "UTF-8",
      "io.netty.leakDetectionLevel" to "PARANOID",
      "kotlinx.coroutines.debug" to "on",
      "sun.io.useCanonCaches" to "false",
      "user.home" to System.getProperty("user.home"),
      TestingOptions.USE_ARCHIVED_COMPILED_CLASSES to "${options.useArchivedCompiledClasses}",
    )) {
      if (v != null) {
        systemProperties.putIfAbsent(k, v)
      }
    }

    systemProperties[TestCaseLoader.TEST_RUNNER_INDEX_FLAG] = options.bucketIndex.toString()
    systemProperties[TestCaseLoader.TEST_RUNNERS_COUNT_FLAG] = options.bucketsCount.toString()

    System.getProperties().forEach { (key, value) ->
      key as String

      if (key.startsWith("pass.")) {
        systemProperties[key.substring("pass.".length)] = value as String
      }

      /**
       * Make test inherit Maven dependency resolver settings
       * See [org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder]
       */
      if (key.startsWith("org.jetbrains.jps.incremental.dependencies.resolution.")) {
        systemProperties.putIfAbsent(key, value as String)
      }
    }

    systemProperties.put(BuildOptions.USE_COMPILED_CLASSES_PROPERTY, context.options.useCompiledClassesFromProjectOutput.toString())

    var suspendDebugProcess = options.isSuspendDebugProcess
    if (options.isPerformanceTestsOnly) {
      context.messages.info("Debugging disabled for performance tests")
      suspendDebugProcess = false
    }
    else if (remoteDebugging) {
      context.messages.info("Remote debugging via TeamCity plugin is activated.")
      if (suspendDebugProcess) {
        context.messages.warning("'intellij.build.test.debug.suspend' option is ignored while debugging via TeamCity plugin")
        suspendDebugProcess = false
      }
    }
    else if (options.isDebugEnabled) {
      val suspend = if (suspendDebugProcess) "y" else "n"
      jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspend},address=${options.debugHost}:${options.debugPort}")
    }

    if (options.isEnableCausalProfiling) {
      val causalProfilingOptions = CausalProfilingOptions.IMPL
      systemProperties.put("intellij.build.test.patterns", causalProfilingOptions.testClass.replace(".", "\\."))
      jvmArgs.addAll(buildCausalProfilingAgentJvmArg(causalProfilingOptions, context))
    }

    jvmArgs.addAll(getCommandLineArgumentsForOpenPackages(context))

    if (suspendDebugProcess) {
      context.messages.info(
        """
        ------------->------------- The process suspended until remote debugger connects to debug port -------------<-------------
        ---------------------------------------^------^------^------^------^------^------^----------------------------------------
        """.trimIndent()
      )
    }
    if (systemProperties.get("java.system.class.loader") == UrlClassLoader::class.java.canonicalName) {
      val utilModule = context.findRequiredModule("intellij.platform.util")
      val enumerator = JpsJavaExtensionService.dependencies(utilModule)
        .recursively()
        .withoutSdk()
        .includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
      val utilClasspath = enumerator.classes().roots.mapTo(LinkedHashSet()) { it.absolutePath }
      utilClasspath.removeAll(HashSet(classPath))
      classPath.addAll(utilClasspath)
    }

    if (context is ArchivedCompilationContext) {
      context.archivesLocation.absolutePathString().let {
        systemProperties.compute("vfs.additional-allowed-roots") { _, old -> if (old == null) it else "$it${File.pathSeparatorChar}$old" }
        systemProperties.put("intellij.test.jars.location", it)
      }
      context.paths.tempDir.resolve("tests.jar.mapping").let { file ->
        Files.createDirectories(file.parent)
        context.saveMapping(file)
        systemProperties.put("intellij.test.jars.mapping.file", file.absolutePathString())
      }
    }
  }

  override suspend fun runTestsSkippedInHeadlessEnvironment() {
    context.compileModules(moduleNames = null, includingTestsInModules = null)
    val tests = spanBuilder("loading all tests annotated with @SkipInHeadlessEnvironment").use { loadTestsSkippedInHeadlessEnvironment() }
    for (it in tests) {
      options.batchTestIncludes = it.getFirst()
      options.mainModule = it.getSecond()
      runTests()
    }
  }

  private suspend fun loadTestsSkippedInHeadlessEnvironment(): List<Pair<String, String>> {
    val classpath = context.project.modules
      .flatMap { context.getModuleRuntimeClasspath(module = it, forTests = true) }
      .distinct()
      .map { Path.of(it) }
    val classloader = UrlClassLoader.build().files(classpath).get()
    val testAnnotation = classloader.loadClass("com.intellij.testFramework.SkipInHeadlessEnvironment")

    return coroutineScope {
      context.project.modules.map { module ->
        async(CoroutineName("loading tests annotated with @SkipInHeadlessEnvironment from the module '${module.name}'")) {
          val root = context.getModuleTestsOutputDir(module)
          if (Files.exists(root)) {
            Files.walk(root).use { stream ->
              stream
                .filter { it.toString().endsWith("Test.class") }
                .map { root.relativize(it).toString() }
                .filter {
                  val className = FileUtilRt.getNameWithoutExtension(it).replace('/', '.')
                  val testClass = classloader.loadClass(className)
                  !Modifier.isAbstract(testClass.modifiers) &&
                  testClass.annotations.any { annotation -> testAnnotation.isAssignableFrom(annotation.javaClass) }
                }
                .map { Pair(it, module.name) }
                .toList()
            }
          }
          else {
            emptyList()
          }
        }
      }
    }.flatMap { it.getCompleted() }
  }

  private fun getTestClassesForModule(mainModule: String, filteringPattern: Pattern = Pattern.compile(".*\\.class")): List<String> {
    val testClasses: List<String> = context.getModuleOutputRoots(context.findRequiredModule(mainModule), forTests = true).flatMap { root ->
      if (root.isRegularFile() && root.extension == "jar") {
        val classes = ArrayList<String>()
        val regex = filteringPattern.toRegex()
        readZipFile(root) { name, _ ->
          if (FileUtilRt.toSystemIndependentName(name).matches(regex)) {
            classes.add(name)
          }
          ZipEntryProcessorResult.CONTINUE
        }
        classes
      }
      else {
        Files.walk(root).use { stream ->
          stream.map { FileUtilRt.toSystemIndependentName(root.relativize(it).toString()) }.filter {
            filteringPattern.matcher(it).matches()
          }.toList()
        } ?: listOf()
      }
    }

    if (testClasses.isEmpty()) {
      throw RuntimeException("No tests were found in module '$mainModule' with $filteringPattern")
    }

    return testClasses
  }

  private suspend fun runInBatchMode(
    mainModule: String,
    systemProperties: Map<String, String>,
    jvmArgs: List<String>,
    envVariables: Map<String, String>,
    bootstrapClasspath: List<String>,
    testClasspath: List<String>,
    devBuildServerSettings: DevBuildServerSettings?,
  ) {
    val pattern = Pattern.compile(FileUtil.convertAntToRegexp(options.batchTestIncludes!!))
    val testClasses = getTestClassesForModule(mainModule = mainModule, filteringPattern = pattern)

    val files = testClasspath.map { Path.of(it) }
    val loader = UrlClassLoader.build().files(files).get()

    @Suppress("UNCHECKED_CAST")
    val testAnnotation4 = loader.loadClass("org.junit.Test") as Class<Annotation>

    @Suppress("UNCHECKED_CAST")
    val testAnnotation5 = loader.loadClass("org.junit.jupiter.api.Test") as Class<Annotation>

    @Suppress("UNCHECKED_CAST")
    val testFactoryAnnotation5 = loader.loadClass("org.junit.jupiter.api.TestFactory") as Class<Annotation>

    var noTestsInAllClasses = true
    for (testClass in testClasses) {
      val qName = FileUtilRt.getNameWithoutExtension(testClass).replace('/', '.')
      try {
        var noTests = true
        val aClass = loader.loadClass(qName)

        val jUnit4And5TestMethods = getAnnotatedTestMethods(aClass, testAnnotation4, testAnnotation5, testFactoryAnnotation5)

        // Run JUnit 4 and 5 whole test classes separately
        if (options.isDedicatedTestRuntime != "false" && jUnit4And5TestMethods.isNotEmpty()) {
          val exitCode = runJUnit5Engine(
            mainModule = mainModule,
            systemProperties = systemProperties,
            jvmArgs = jvmArgs,
            envVariables = envVariables,
            bootstrapClasspath = bootstrapClasspath,
            modulePath = null,
            testClasspath = testClasspath,
            suiteName = qName,
            methodName = null,
            devBuildSettings = devBuildServerSettings,
          )
          noTests = exitCode == NO_TESTS_ERROR
        }
        // Run JUnit 4 and 5 test methods separately if any
        else if (jUnit4And5TestMethods.isNotEmpty()) {
          for (method in jUnit4And5TestMethods) {
            val exitCode = runJUnit5Engine(
              mainModule = mainModule,
              systemProperties = systemProperties,
              jvmArgs = jvmArgs,
              envVariables = envVariables,
              bootstrapClasspath = bootstrapClasspath,
              modulePath = null,
              testClasspath = testClasspath,
              suiteName = qName,
              methodName = method,
              devBuildSettings = devBuildServerSettings,
            )
            noTests = noTests && exitCode == NO_TESTS_ERROR
          }
        }

        // Fallback to running whole class (JUnit 3+4)
        if (noTests) {
          val exitCode = runJUnit5Engine(
            mainModule = mainModule,
            systemProperties = systemProperties,
            jvmArgs = jvmArgs,
            envVariables = envVariables,
            bootstrapClasspath = bootstrapClasspath,
            modulePath = null,
            testClasspath = testClasspath,
            suiteName = qName,
            methodName = null,
            devBuildSettings = devBuildServerSettings,
          )
          noTests = exitCode == NO_TESTS_ERROR
        }
        noTestsInAllClasses = noTestsInAllClasses && noTests
      }
      catch (e: Throwable) {
        throw RuntimeException("Failed to process $qName", e)
      }
    }

    if (noTestsInAllClasses) {
      throw RuntimeException("No tests were found in $mainModule with $pattern")
    }
  }

  private fun getAnnotatedTestMethods(aClass: Class<*>, vararg annotations: Class<Annotation>): List<String> {
    return aClass.methods
      .asSequence()
      .filter { m -> Modifier.isPublic(m.modifiers) }
      .filter { m -> annotations.any { a -> m.isAnnotationPresent(a) } }
      .map { m -> m.name }
      .toList()
  }

  private suspend fun runJUnit5Engine(
    mainModule: String,
    systemProperties: Map<String, String>,
    jvmArgs: List<String>,
    envVariables: Map<String, String>,
    bootstrapClasspath: List<String>,
    modulePath: List<String>?,
    testClasspath: List<String>,
    devBuildServerSettings: DevBuildServerSettings?,
  ) {
    if (isRunningInBatchMode) {
      spanBuilder("run tests in batch mode")
        .setAttribute(AttributeKey.stringKey("pattern"), options.batchTestIncludes ?: "")
        .use {
          runInBatchMode(
            mainModule = mainModule,
            systemProperties = systemProperties,
            jvmArgs = jvmArgs,
            envVariables = envVariables,
            bootstrapClasspath = bootstrapClasspath,
            testClasspath = testClasspath,
            devBuildServerSettings = devBuildServerSettings,
          )
        }
    }
    else {
      val messages = context.messages
      if (options.isDedicatedTestRuntime != "false") {
        if (options.isDedicatedTestRuntime != "class" && options.isDedicatedTestRuntime != "package") {
          messages.logErrorAndThrow("Unsupported 'intellij.build.test.dedicated.runtime' value: ${options.isDedicatedTestRuntime}. Expected 'class', 'package' or 'false'")
        }
        messages.info("Will run tests in dedicated runtimes ('${options.isDedicatedTestRuntime}')")
        // First, collect all tests for both JUnit5 and JUnit3+4
        val testClassesJUnit5 = spanBuilder("collect junit 5 tests").use {
          if (options.shouldSkipJUnit5Tests) {
            messages.warning("JUnit 5 tests collections is skipped")
            return@use emptyList()
          }

          val testClassesListFile = Files.createTempFile("tests-to-run-", ".list").apply { Files.delete(this) }
          runJUnit5Engine(
            mainModule = mainModule,
            systemProperties = systemProperties + ("intellij.build.test.list.classes" to testClassesListFile.absolutePathString()),
            jvmArgs = jvmArgs,
            envVariables = envVariables,
            bootstrapClasspath = bootstrapClasspath,
            modulePath = modulePath,
            testClasspath = testClasspath,
            suiteName = null,
            methodName = null,
            devBuildSettings = null,
          )
          testClassesListFile.let { if (Files.exists(it)) it.readLines() else emptyList() }
        }

        val testClassesJUnit34 = block("collect junit 3+4 tests") {
          if (options.shouldSkipJUnit34Tests) {
            messages.warning("JUnit 3+4 tests collections is skipped")
            return@block emptyList()
          }

          val testClassesListFile = Files.createTempFile("tests-to-run-", ".list").apply { Files.delete(this) }
          runJUnit5Engine(
            mainModule = mainModule,
            systemProperties = systemProperties + ("intellij.build.test.list.classes" to testClassesListFile.absolutePathString()),
            jvmArgs = jvmArgs,
            envVariables = envVariables,
            bootstrapClasspath = bootstrapClasspath,
            modulePath = modulePath,
            testClasspath = testClasspath,
            suiteName = options.bootstrapSuite,
            methodName = null,
            devBuildSettings = null,
          )
          return@block testClassesListFile.let { if (Files.exists(it)) it.readLines() else emptyList() }
        }

        if (testClassesJUnit5.isEmpty() && testClassesJUnit34.isEmpty() &&
            // a bucket might be empty for run configurations with too few tests due to imperfect tests balancing
            options.bucketsCount < 2) {
          throw NoTestsFound()
        }

        if (options.isDedicatedTestRuntime == "class") {
          suspend fun runOneClass(testClassName: String) {
            val exitCode = block("running test class '$testClassName'") {
              runJUnit5Engine(
                mainModule = mainModule,
                systemProperties = systemProperties + ("idea.performance.tests.discovery.filter" to "true"),
                jvmArgs = jvmArgs,
                envVariables = envVariables,
                bootstrapClasspath = bootstrapClasspath,
                modulePath = modulePath,
                testClasspath = testClasspath,
                suiteName = testClassName,
                methodName = null,
                devBuildSettings = devBuildServerSettings,
              )
            }
            if (exitCode == NO_TESTS_ERROR) throw NoTestsFound()
          }

          if (testClassesJUnit5.isNotEmpty()) {
            messages.info("Will run JUnit 5 tests:\n${testClassesJUnit5.joinToString("\n")}")
            for (s in testClassesJUnit5) {
              runOneClass(s)
            }
          }
          if (testClassesJUnit34.isNotEmpty()) {
            messages.info("Will run JUnit 3+4 tests:\n${testClassesJUnit34.joinToString("\n")}")
            for (s in testClassesJUnit34) {
              runOneClass(s)
            }
          }
        }
        else if (options.isDedicatedTestRuntime == "package") {
          fun groupByPackages(tests: List<String>): Map<String, List<String>> {
            return tests.groupBy {
              val i = it.lastIndexOf('.')
              if (i != -1) it.substring(0, i) else ""
            }
          }

          suspend fun runOnePackage(entry: Map.Entry<String, List<String>>) {
            val packageName = entry.key
            val classes = entry.value

            val exitCode = block("running tests in package '$packageName'") {
              runJUnit5Engine(
                mainModule = mainModule,
                systemProperties = systemProperties + ("idea.performance.tests.discovery.filter" to "true"),
                jvmArgs = jvmArgs,
                envVariables = envVariables,
                bootstrapClasspath = bootstrapClasspath,
                modulePath = modulePath,
                testClasspath = testClasspath,
                suiteName = "__classes__",
                methodName = classes.joinToString(";"),
                devBuildSettings = devBuildServerSettings,
              )
            }
            if (exitCode == NO_TESTS_ERROR) throw NoTestsFound()
          }

          if (testClassesJUnit5.isNotEmpty()) {
            val packages = groupByPackages(testClassesJUnit5)
            messages.info(packages.entries.joinToString(prefix = "Will run JUnit 5 packages:\n", separator = "\n") { e ->
              e.value.joinToString(prefix = "${e.key}\n  ", separator = "\n  ")
            })
            for (entry in packages) {
              runOnePackage(entry)
            }
          }
          if (testClassesJUnit34.isNotEmpty()) {
            val packages = groupByPackages(testClassesJUnit34)
            messages.info(packages.entries.joinToString(prefix = "Will run JUnit 3+4 packages:\n", separator = "\n") { e ->
              e.value.joinToString(prefix = "${e.key}\n  ", separator = "\n  ")
            })
            for (entry in packages) {
              runOnePackage(entry)
            }
          }
        }
      }
      else {
        val failedClassesJUnit5List = Files.createTempFile("failed-classes-junit5-", ".list").apply { Files.delete(this) }
        val failedClassesJUnit34List = Files.createTempFile("failed-classes-junit34-", ".list").apply { Files.delete(this) }
        val additionalPropertiesJUnit5: Map<String, String> = failedClassesJUnit5List.let {
          if (options.attemptCount > 1) mapOf("intellij.build.test.retries.failedClasses.file" to "$it", "intellij.build.test.list.file" to "$it")
          else emptyMap()
        }
        val additionalPropertiesJUnit34: Map<String, String> = failedClassesJUnit34List.let {
          if (options.attemptCount > 1) mapOf("intellij.build.test.retries.failedClasses.file" to "$it", "intellij.build.test.list.file" to "$it")
          else emptyMap()
        }
        var runJUnit5 = !options.shouldSkipJUnit5Tests
        var runJUnit34 = !options.shouldSkipJUnit34Tests
        for (attempt in 1..options.attemptCount) {
          if (!runJUnit5 && !runJUnit34) break
          val spanNameSuffix = if (options.attemptCount > 1) " (attempt $attempt)" else ""
          val additionalProperties: Map<String, String> = if (attempt > 1) mapOf("intellij.build.test.ignoreFirstAndLastTests" to "true") else emptyMap()

          val exitCode5: Int = if (runJUnit5) {
            block("run junit 5 tests${spanNameSuffix}") {
              runJUnit5Engine(
                mainModule = mainModule,
                systemProperties = systemProperties + additionalProperties + additionalPropertiesJUnit5,
                jvmArgs = jvmArgs,
                envVariables = envVariables,
                bootstrapClasspath = bootstrapClasspath,
                modulePath = modulePath,
                testClasspath = testClasspath,
                suiteName = null,
                methodName = null,
                devBuildSettings = devBuildServerSettings,
              )
            }
          }
          else {
            0
          }

          val exitCode34: Int = if (runJUnit34) {
            block("run junit 3+4 tests${spanNameSuffix}") {
              runJUnit5Engine(
                mainModule = mainModule,
                systemProperties = systemProperties + additionalProperties + additionalPropertiesJUnit34,
                jvmArgs = jvmArgs,
                envVariables = envVariables,
                bootstrapClasspath = bootstrapClasspath,
                modulePath = modulePath,
                testClasspath = testClasspath,
                suiteName = options.bootstrapSuite,
                methodName = null,
                devBuildSettings = devBuildServerSettings,
              )
            }
          }
          else {
            0
          }

          if (exitCode5 == NO_TESTS_ERROR && exitCode34 == NO_TESTS_ERROR &&
              // only check on the first (full) attempt
              attempt == 1 &&
              // a bucket might be empty for run configurations with too few tests due to imperfect tests balancing
              options.bucketsCount < 2) {
            throw NoTestsFound()
          }

          if (runJUnit5) {
            val failedClassesJUnit5 = failedClassesJUnit5List.let { if (Files.exists(it)) it.readLines() else emptyList() }
            if (failedClassesJUnit5.isNotEmpty()) {
              messages.info("Will rerun JUnit 5 tests: $failedClassesJUnit5")
            }
            else {
              runJUnit5 = false
            }
          }

          if (runJUnit34) {
            val failedClassesJUnit34 = failedClassesJUnit34List.let { if (Files.exists(it)) it.readLines() else emptyList() }
            if (failedClassesJUnit34.isNotEmpty()) {
              messages.info("Will rerun JUnit 3+4 tests: $failedClassesJUnit34")
            }
            else {
              runJUnit34 = false
            }
          }
        }
      }
    }
  }

  private class NoTestsFound : Exception()

  /**
   * we need to gather all the jars from original classpath entries into one directory
   * to be able to pass them as a classpath argument using asterisk mask.
   * otherwise classpath may be too long and contradict with maximal allowed parameter size (see ARG_MAX)
   */
  @OptIn(ExperimentalPathApi::class)
  private fun prepareMuslClassPath(classpath: List<String>): List<String> {
    val muslClasspathEntries = ArrayList<String>()

    val muslClassPath = ULTIMATE_HOME.resolve("musl_classpath_${Random.nextInt(Int.MAX_VALUE)}").let {
      if (it.exists()) {
        it.deleteRecursively()
      }
      Files.createDirectory(it)
    }

    muslClasspathEntries.add("${muslClassPath.absolutePathString()}/*")

    for (classPathFile in classpath) {
      val file = Path.of(classPathFile)
      if (file.isRegularFile()) {
        // copy the original classpath entry to the directory, which is already included in the resulting classpath above
        file.copyTo(muslClassPath.resolve(file.fileName.toString()), overwrite = true)
      }
      else {
        muslClasspathEntries.add(classPathFile)
      }
    }
    return muslClasspathEntries
  }

  private suspend fun runJUnit5Engine(
    mainModule: String,
    systemProperties: Map<String, String?>,
    jvmArgs: List<String>,
    envVariables: Map<String, String>,
    bootstrapClasspath: List<String>,
    modulePath: List<String>?,
    testClasspath: List<String>,
    suiteName: String?,
    methodName: String?,
    devBuildSettings: DevBuildServerSettings?,
  ): Int {
    val useDevMode = devBuildSettings != null && devBuildSettings.mainClass.isNotEmpty()
    if (useDevMode) {
      val bootClasspath = context.getModuleRuntimeClasspath(module = context.findRequiredModule(IJENT_BOOT_CLASSPATH_MODULE), forTests = false)
      val classpath = context.getModuleRuntimeClasspath(module = context.findRequiredModule(devBuildSettings.mainClassModule), forTests = false)
        .filter { !bootClasspath.contains(it) }

      val messages = context.messages
      messages.info("Effective main module: $mainModule")
      messages.info("Effective classpath:\n${classpath.joinToString("\n")}")
      messages.info("Effective boot classpath:\n${bootClasspath.joinToString("\n")}")

      val args = jvmArgs.toMutableList()
      args.add("-Didea.dev.mode.in.process.build.boot.classpath.correct=true")
      args.add("-Xbootclasspath/a:${bootClasspath.joinToString(File.pathSeparator)}")

      return doRunJUnit5Engine(
        mainModule = mainModule,
        systemProperties = systemProperties,
        jvmArgs = args,
        envVariables = envVariables,
        modulePath = modulePath,
        suiteName = suiteName,
        methodName = methodName,
        devBuildModeSettings = devBuildSettings,
        classpath = classpath,
      )
    }
    else {
      val classpath = ArrayList<String>(bootstrapClasspath)
      if (modulePath == null) {
        appendJUnitStarter(classpath, context)
      }

      if (!isBootstrapSuiteDefault || isRunningInBatchMode || options.isDedicatedTestRuntime != "false" || suiteName == null) {
        classpath.addAll(testClasspath)
      }

      return doRunJUnit5Engine(
        mainModule = mainModule,
        systemProperties = systemProperties,
        jvmArgs = jvmArgs,
        envVariables = envVariables,
        modulePath = modulePath,
        suiteName = suiteName,
        methodName = methodName,
        devBuildModeSettings = null,
        classpath = if (LibcImpl.current(OsFamily.currentOs) == LinuxLibcImpl.MUSL) prepareMuslClassPath(classpath) else classpath,
      )
    }
  }

  private suspend fun doRunJUnit5Engine(
    mainModule: String,
    systemProperties: Map<String, String?>,
    jvmArgs: List<String>,
    envVariables: Map<String, String>,
    modulePath: List<String>?,
    classpath: List<String>,
    suiteName: String?,
    methodName: String?,
    devBuildModeSettings: DevBuildServerSettings?,
  ): Int {
    val args = ArrayList<String>()
    args.add("-classpath")
    args.add(classpath.joinToString(separator = File.pathSeparator))

    if (modulePath != null) {
      args.add("--module-path")
      val mp = ArrayList<String>(modulePath)
      appendJUnitStarter(mp, context)
      args.add(mp.joinToString(separator = File.pathSeparator))
      args.add("--add-modules=ALL-MODULE-PATH")
    }

    args.addAll(MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS)
    args.addAll(jvmArgs)

    args.add("-Dintellij.build.test.runner=junit5")

    for ((k, v) in systemProperties) {
      if (v != null) {
        args += "-D${k}=${v}"
      }
    }

    args += "--add-opens"
    args += "java.base/java.nio.file.spi=ALL-UNNAMED"

    val environment: MutableMap<String, String> = HashMap(envVariables)

    val mainClass = if (suiteName == null) "com.intellij.tests.JUnit5TeamCityRunnerForTestsOnClasspath" else "com.intellij.tests.JUnit5TeamCityRunnerForTestAllSuite"
    if (devBuildModeSettings == null) {
      args.add(mainClass)
    }
    else {
      devBuildModeSettings.apply(mainClass, mainModule, args, environment)
    }

    if (suiteName != null) {
      args.add(suiteName)
    }

    if (methodName != null) {
      args.add(methodName)
    }

    val argFile = CommandLineWrapperUtil.createArgumentFile(args, Charset.defaultCharset())
    val runtime = getRuntimeExecutablePath().toString()

    context.messages.info("Starting tests on runtime $runtime")
    val builder = ProcessBuilder(runtime, "@" + argFile.absolutePath)
    builder.environment().putAll(environment)
    builder.inheritIO()
    val exitCode = builder.start().awaitExit()
    if (exitCode != 0 && exitCode != NO_TESTS_ERROR) {
      context.messages.warning("Tests failed with exit code $exitCode")
    }
    return exitCode
  }

  private val isBootstrapSuiteDefault: Boolean
    get() = options.bootstrapSuite == TestingOptions.BOOTSTRAP_SUITE_DEFAULT

  private val isRunningInBatchMode: Boolean
    get() {
      return options.batchTestIncludes != null &&
             options.testPatterns == null &&
             options.testConfigurations == null &&
             options.testGroups == TestingOptions.ALL_EXCLUDE_DEFINED_GROUP
    }
}

private fun appendJUnitStarter(path: MutableList<String>, context: CompilationContext) {
  for (libName in listOf("JUnit5", "JUnit5Launcher", "JUnit5Vintage", "JUnit5Jupiter")) {
    for (library in context.projectModel.project.libraryCollection.findLibrary(libName)!!.getFiles(JpsOrderRootType.COMPILED)) {
      path.add(library.absolutePath)
    }
  }
}

private fun buildCausalProfilingAgentJvmArg(options: CausalProfilingOptions, context: CompilationContext): List<String> {
  val causalProfilingJvmArgs = ArrayList<String>()

  @Suppress("SpellCheckingInspection")
  val causalProfilerAgentName = if (SystemInfoRt.isLinux || SystemInfoRt.isMac) "liblagent.so" else null
  if (causalProfilerAgentName == null) {
    context.messages.info("Causal profiling is supported for Linux and Mac only")
  }
  else {
    val agentArgs = options.buildAgentArgsString()
    causalProfilingJvmArgs += "-agentpath:${System.getProperty("teamcity.build.checkoutDir")}/${causalProfilerAgentName}=${agentArgs}"
  }
  return causalProfilingJvmArgs
}

private class MyTraceFileUploader(
  serverUrl: String,
  token: String?,
  private val messages: BuildMessages,
) : TraceFileUploader(serverUrl, token) {
  override fun log(message: String) = messages.info(message)
}

private val ignoredPrefixes = listOf(
  "-ea", "-XX:+HeapDumpOnOutOfMemoryError", "-Xbootclasspath", "-Xmx", "-Xms",
  // ReservedCodeCacheSize is critical - if not configured, maybe error `Out of space in CodeCache for adapters`
  "-XX:ReservedCodeCacheSize",
  "-Didea.system.path=", "-Didea.config.path=", "-Didea.home.path="
)

private fun removeStandardJvmOptions(vmOptions: List<String>): List<String> {
  return vmOptions.filter { option -> ignoredPrefixes.none(option::startsWith) }
}

private suspend fun publishTestDiscovery(messages: BuildMessages, file: String?) {
  val serverUrl = System.getProperty("intellij.test.discovery.url")
  val token = System.getProperty("intellij.test.discovery.token")
  messages.info("Trying to upload $file into ${serverUrl}.")
  val path = file?.let { Path.of(it) }
  if (path != null && Files.exists(path)) {
    if (serverUrl == null) {
      messages.warning(
        """
        Test discovery server url is not defined, but test discovery capturing enabled. 
        Will not upload to remote server. Please set 'intellij.test.discovery.url' system property.
        """.trimIndent()
      )
      return
    }

    val uploader = MyTraceFileUploader(serverUrl, token, messages)
    try {
      val map = LinkedHashMap<String, String>(7)
      map["teamcity-build-number"] = System.getProperty("build.number")
      map["teamcity-build-type-id"] = System.getProperty("teamcity.buildType.id")
      @Suppress("SpellCheckingInspection")
      map["teamcity-build-configuration-name"] = System.getenv("TEAMCITY_BUILDCONF_NAME")
      map["teamcity-build-project-name"] = System.getenv("TEAMCITY_PROJECT_NAME")
      map["branch"] = System.getProperty("teamcity.build.branch")?.takeIf(String::isNotEmpty) ?: "master"
      map["project"] = System.getProperty("intellij.test.discovery.project")?.takeIf(String::isNotEmpty) ?: "intellij"
      map["checkout-root-prefix"] = System.getProperty("intellij.build.test.discovery.checkout.root.prefix") ?: ""
      uploader.upload(path, map)
    }
    catch (e: Exception) {
      messages.logErrorAndThrow(e.message!!, e)
    }
  }
  messages.buildStatus("With Discovery, {build.status.text}")
}

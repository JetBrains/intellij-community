// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "BlockingMethodInNonBlockingContext")

package org.jetbrains.intellij.build.impl

import com.intellij.ClassFinder
import com.intellij.GroupBasedTestClassFilter
import com.intellij.TestCaseLoader
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.idea.IJIgnore
import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.platform.util.coroutines.filterConcurrent
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.util.bazelEnvironment.BazelRunfiles
import com.intellij.util.io.awaitExit
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.trace.Span
import jetbrains.buildServer.messages.serviceMessages.BlockClosed
import jetbrains.buildServer.messages.serviceMessages.BlockOpened
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.incremental.java.ModulePathSplitter
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.PrintStream
import java.lang.reflect.Modifier
import java.nio.charset.Charset
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
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
      coveredModuleNames = options.coveredModuleNames
                             ?.splitToSequence(';')
                             ?.map { it.trim() }
                             ?.toList()
                             ?.takeIf { it.any() }
                           ?: runConfigurations
                             .map { it.moduleName }
                             .takeIf { it.any() }
                           ?: listOfNotNull(options.mainModule),
      coveredClasses = requireNotNull(options.coveredClassesPatterns) {
        "Test coverage is enabled but the classes pattern is not specified"
      }.splitToSequence(';').map(::Regex).toList(),
    )
  }

  private val runConfigurations: List<JUnitRunConfigurationProperties> by lazy {
    options.testConfigurations
      ?.splitToSequence(';')
      ?.filter(String::isNotEmpty)
      ?.flatMap { loadRunConfigurations(name = it, projectHome = context.paths.projectHome) }
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
      blockWithDefaultFlowId("compile modules") {
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
        errorOptionIgnored(testConfigurationsOptionName, "intellij.build.test.patterns")
      }
      if (options.testSimplePatterns != null) {
        errorOptionIgnored(testConfigurationsOptionName, "intellij.build.test.simple.patterns")
      }
      if (options.testGroups != null) {
        errorOptionIgnored(testConfigurationsOptionName, "intellij.build.test.groups")
      }
      if (mainModule != null && !options.validateMainModule) {
        errorOptionIgnored(testConfigurationsOptionName, "intellij.build.test.main.module")
      }
      if (options.searchScope != JUnitRunConfigurationProperties.TestSearchScope.MODULE_WITH_DEPENDENCIES.serialized) {
        errorOptionIgnored(testConfigurationsOptionName, "intellij.build.test.search.scope")
      }
    }
    else if (options.testPatterns != null) {
      if (options.testSimplePatterns != null) {
        errorOptionIgnored("intellij.build.test.patterns", "intellij.build.test.simple.patterns")
      }
      if (options.testGroups != null) {
        errorOptionIgnored("intellij.build.test.patterns", "intellij.build.test.groups")
      }
    }
    else if (options.testSimplePatterns != null) {
      if (TeamCityHelper.isUnderTeamCity) {
        context.messages.logErrorAndThrow("'intellij.build.test.simple.patterns' option should be used only for local runs")
      }
      if (options.testGroups != null) {
        errorOptionIgnored("intellij.build.test.simple.patterns", "intellij.build.test.groups")
      }
    }

    if (options.testConfigurations == null && options.testPatterns == null && options.testSimplePatterns == null && options.testGroups == null) {
      context.messages.logErrorAndThrow("'intellij.build.test.configurations', 'intellij.build.test.patterns', 'intellij.build.test.simple.patterns', or 'intellij.build.test.groups' option should be set")
    }

    if (options.validateMainModule && mainModule.isNullOrEmpty()) {
      context.messages.logErrorAndThrow("'intellij.build.test.main.module.validate' option requires 'intellij.build.test.main.module' to be set")
    }

    if (options.searchScope != JUnitRunConfigurationProperties.TestSearchScope.MODULE_WITH_DEPENDENCIES.serialized) {
      if (options.searchScope != JUnitRunConfigurationProperties.TestSearchScope.SINGLE_MODULE.serialized) {
        context.messages.logErrorAndThrow("Unsupported 'intellij.build.test.search.scope' value: ${options.searchScope}")
      }
    }
    if (options.repeatCount < 1) {
      context.messages.logErrorAndThrow("'intellij.build.test.repeat.count' option should be greater than 0, actual: ${options.repeatCount}")
    }
    if (options.repeatCount > 1 && options.attemptCount > 1) {
      context.messages.logErrorAndThrow("'intellij.build.test.repeat.count' and 'intellij.build.test.attempt.count' options cannot be used together")
    }

    if (options.shouldSkipJUnit34Tests && options.shouldSkipJUnit5Tests) {
      context.messages.logErrorAndThrow("'intellij.build.test.skip.tests.junit34' and 'intellij.build.test.skip.tests.junit5' options cannot be used together")
    }
  }

  private fun errorOptionIgnored(specifiedOption: String, ignoredOption: String) {
    context.messages.logErrorAndThrow("'${specifiedOption}' option is specified, so '${ignoredOption}' will be ignored.")
  }

  private suspend fun runTestsFromRunConfigurations(
    additionalJvmOptions: List<String>,
    runConfigurations: List<JUnitRunConfigurationProperties>,
    systemProperties: MutableMap<String, String>,
  ) {
    for (configuration in runConfigurations) {
      blockWithDefaultFlowId("run '${configuration.name}' run configuration") {
        runTestsFromRunConfiguration(runConfigurationProperties = configuration, additionalJvmOptions = additionalJvmOptions, systemProperties = systemProperties)
      }
    }
  }

  private suspend fun runTestsFromRunConfiguration(
    runConfigurationProperties: JUnitRunConfigurationProperties,
    additionalJvmOptions: List<String>,
    systemProperties: Map<String, String>,
  ) {
    try {
      runTestsProcess(
        mainModule = context.findRequiredModule(runConfigurationProperties.moduleName),
        testGroups = null,
        testPatterns = runConfigurationProperties.testClassPatterns.joinToString(separator = ";"),
        jvmArgs = removeStandardJvmOptions(runConfigurationProperties.vmParameters) + additionalJvmOptions
                  + "-Dintellij.build.run.configuration.name=${runConfigurationProperties.name}",
        systemProperties = systemProperties,
        envVariables = runConfigurationProperties.envVariables,
        remoteDebugging = false,
        searchForTestsAcrossModuleDependencies = runConfigurationProperties.testSearchScope == JUnitRunConfigurationProperties.TestSearchScope.MODULE_WITH_DEPENDENCIES,
        rootExcludeCondition = null,
      )
    }
    catch (e: NoTestsFound) {
      throw RuntimeException("No tests were found in the configuration '${runConfigurationProperties.name}'").apply {
        addSuppressed(e)
      }
    }
  }

  private suspend fun guessTestModulesForGroupsAndPatterns(
    mainModule: JpsModule,
    rootExcludeCondition: ((Path) -> Boolean)?,
    systemProperties: Map<String, String>,
  ): List<JpsModule> {
    fun setPropertyFromPass(property: String) = System.getProperty("pass.$property")?.run {
      if (System.getProperty(property, this) != this) {
        context.messages.logErrorAndThrow("'$property' and 'pass.$property' mismatch: ${System.getProperty(property)} != $this")
      }

      System.setProperty(property, this)
    }

    // configure TestCaseLoader#isClassNameIncluded with the properties from the test process
    "test.group.roots".let { systemProperties[it]?.run { System.setProperty(it, this) } }  // from systemProperties
    "intellij.build.test.patterns".let { options.testPatterns?.run { System.setProperty(it, this) } }  // from options, e.g. TestingTasksImpl#runTestsSkippedInHeadlessEnvironment
    "intellij.build.test.groups".let { options.testGroups?.run { System.setProperty(it, this) } }  // from options, e.g. RunAnyTestTheSameWayTeamCityDoes#run
    setPropertyFromPass(TestCaseLoader.INCLUDE_UNCONVENTIONALLY_NAMED_TESTS_FLAG)

    // configure TestCaseLoader#matchesCurrentBucket with the properties from the test process
    listOf(
      "idea.bucketing.season",
      "idea.bucketing.season.fallback",
      TestCaseLoader.IS_TESTS_DURATION_BUCKETING_ENABLED_FLAG
    ).forEach(::setPropertyFromPass)

    return JpsJavaExtensionService.dependencies(mainModule).recursively().modules
      .filterConcurrent {
        if (rootExcludeCondition != null) {
          val contentRoot = it.contentRootsList.urls.firstOrNull()?.let(JpsPathUtil::urlToNioPath)
          if (contentRoot != null && rootExcludeCondition(contentRoot)) return@filterConcurrent false  // root excluded
        }

        for (outputRoot in context.outputProvider.getModuleOutputRoots(it, forTests = true)) {
          val classNames = FileSystems.newFileSystem(outputRoot).use { fs ->
            fs.rootDirectories.map(Files::walk).flatMap { stream ->
              stream.filter { it.toString().endsWith(".class") }.map { classFile ->
                classFile.toString().removePrefix("/").replace("/", ".").removeSuffix(".class")
              }.toList()
            }
          }

          // same as `com.intellij.tests.JUnit5TeamCityRunner.CommonTestClassesFilter` and `com.intellij.tests.JUnit5TeamCityRunner.BucketingClassNameFilter`
          if (classNames.any { TestCaseLoader.isClassNameIncluded(it) && TestCaseLoader.matchesCurrentBucket(it) }) return@filterConcurrent true
        }

        false
      }.sortedBy { it.name }
  }

  private suspend fun runTestsFromGroupsAndPatterns(
    additionalJvmOptions: List<String>,
    mainModule: String,
    rootExcludeCondition: ((Path) -> Boolean)?,
    systemProperties: MutableMap<String, String>,
  ) {
    val mainModule = context.findRequiredModule(mainModule)

    if (options.testGroups != null) {
      val testGroupRoots = let {
        if (options.testGroups!!.contains(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED)) context.project.modules
        else JpsJavaExtensionService.dependencies(mainModule).recursively().modules.toList()
      }.mapConcurrent {
        it.sourceRoots
          .filter { it.rootType is JavaResourceRootType }
          .map { it.path.resolve(TestCaseLoader.COMMON_TEST_GROUPS_RESOURCE_NAME) }
          .filter(Files::exists)
      }.flatten().sorted()

      systemProperties.put("test.group.roots", testGroupRoots.joinToString(File.pathSeparator, transform = Path::absolutePathString))
    }

    val searchForTestsAcrossModuleDependencies = options.searchScope == JUnitRunConfigurationProperties.TestSearchScope.MODULE_WITH_DEPENDENCIES.serialized
    val testModules = let {
      if (searchForTestsAcrossModuleDependencies && System.getProperty("pass.jar.dependencies.to.tests") == null) guessTestModulesForGroupsAndPatterns(mainModule, rootExcludeCondition, systemProperties)
      else listOf(mainModule)
    }

    context.messages.info("Will run tests from simple patterns, patterns, or groups in ${testModules.size} modules: ${testModules.joinToString(", ") { it.name }}")
    val suppressedExceptions = mutableListOf<Throwable>()
    for (testModule in testModules) {
      blockWithDefaultFlowId("run '${testModule.name}' module") {
        try {
          runTestsProcess(
            mainModule = testModule,
            runContextModule = mainModule,
            testGroups = options.testGroups,
            testPatterns = options.testPatterns,
            testTags = options.testTags,
            jvmArgs = additionalJvmOptions,
            systemProperties = systemProperties,
            remoteDebugging = false,
            searchForTestsAcrossModuleDependencies = false,
            rootExcludeCondition = rootExcludeCondition,
          )
        }
        catch (e: NoTestsFound) {
          suppressedExceptions.add(RuntimeException("No tests were found in '${testModule.name}' module", e))
        }
      }
    }

    if (suppressedExceptions.size == testModules.size &&
        // a bucket might be empty for run configurations with too few tests due to imperfect tests balancing
        options.bucketsCount < 2) {
      throw RuntimeException("No tests were found in '${mainModule.name}' module classpath w/ simple patterns '${options.testSimplePatterns}', patterns '${options.testPatterns}', or groups '${options.testGroups}'").apply {
        suppressedExceptions.forEach(::addSuppressed)
      }
    }
  }

  private fun loadTestDiscovery(additionalJvmOptions: MutableList<String>, systemProperties: MutableMap<String, String>) {
    val testDiscovery = "intellij-test-discovery"
    val agentJar = context.outputProvider.findLibraryRoots(testDiscovery, moduleLibraryModuleName = null)
                     .firstOrNull {
                       val name = it.fileName.toString()
                       name.startsWith("intellij-test-discovery") && name.endsWith(".jar")
                     } ?: throw RuntimeException("Can't find the agent in $testDiscovery library, but test discovery capturing enabled.")

    additionalJvmOptions.add("-javaagent:${agentJar}")
    val excludeRoots = context.projectModel.global.libraryCollection.getLibraries(JpsJavaSdkType.INSTANCE)
      .mapTo(LinkedHashSet()) { FileUtilRt.toSystemDependentName(it.properties.homePath) }
    excludeRoots.add(context.paths.buildOutputDir.toString())
    excludeRoots.add(context.paths.projectHome.resolve("out").toString())

    systemProperties.put("test.discovery.listener", "com.intellij.TestDiscoveryBasicListener")
    systemProperties.put("test.discovery.data.listener", "com.intellij.rt.coverage.data.SingleTrFileDiscoveryProtocolDataListener")
    systemProperties.put("org.jetbrains.instrumentation.trace.file", testDiscoveryTraceFilePath)

    options.testDiscoveryIncludePatterns?.let { systemProperties.put("test.discovery.include.class.patterns", it) }
    options.testDiscoveryExcludePatterns?.let { systemProperties.put("test.discovery.exclude.class.patterns", it) }

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
      mainModule = context.findRequiredModule(mainModule),
      testGroups = null,
      testPatterns = junitClass,
      jvmArgs = removeStandardJvmOptions(StringUtilRt.splitHonorQuotes(remoteDebugJvmOptions, ' ')) + additionalJvmOptions,
      systemProperties = emptyMap(),
      remoteDebugging = true,
      searchForTestsAcrossModuleDependencies = true,
      rootExcludeCondition = null,
    )
  }

  private suspend fun runTestsProcess(
    mainModule: JpsModule,
    runContextModule: JpsModule = mainModule,
    testGroups: String?,
    testPatterns: String?,
    testTags: String? = null,
    jvmArgs: List<String>,
    systemProperties: Map<String, String>,
    envVariables: Map<String, String> = emptyMap(),
    remoteDebugging: Boolean,
    searchForTestsAcrossModuleDependencies: Boolean,
    rootExcludeCondition: ((Path) -> Boolean)?,
  ) {
    val outputProvider = context.outputProvider

    val modulePath: List<String>?
    var testClasspath = buildList {
      addAll(context.getModuleRuntimeClasspath(runContextModule, forTests = true))

      //module with "com.intellij.TestAll" which output should be found in `testClasspath + modulePath`
      val testFrameworkCoreModule = outputProvider.findRequiredModule("intellij.platform.testFramework.core")
      addAll(context.getModuleRuntimeClasspath(testFrameworkCoreModule, false) )
    }.distinct()

    val moduleInfoFile = JpsJavaExtensionService.getInstance().getJavaModuleIndex(context.project).getModuleInfoFile(mainModule, true)
    val toExistingAbsolutePathConverter: (Path) -> String = { require(Files.exists(it)); it.toAbsolutePath().normalize().toString() }
    if (moduleInfoFile != null) {
      val outputDir = outputProvider.getModuleOutputRoots(mainModule, forTests = true).single().let(Path::toFile)
      val pair = ModulePathSplitter().splitPath(moduleInfoFile, mutableSetOf(outputDir), testClasspath.map {
        @Suppress("IO_FILE_USAGE")
        it.toFile()
      })
      modulePath = pair.first.path.map { it.toPath() }.map(toExistingAbsolutePathConverter)
      testClasspath = pair.second.map { it.toPath() }
    }
    else {
      modulePath = null
    }

    val testRoots = let {
      if (searchForTestsAcrossModuleDependencies) JpsJavaExtensionService.dependencies(mainModule).recursively().modules
      else listOf(mainModule)
    }.flatMap {
      if (rootExcludeCondition != null) {
        val contentRoot = it.contentRootsList.urls.firstOrNull()?.let(JpsPathUtil::urlToNioPath)
        if (contentRoot != null && rootExcludeCondition(contentRoot)) return@flatMap emptyList()  // root excluded
      }

      context.outputProvider.getModuleOutputRoots(it, forTests = true)
    }

    val devBuildServerSettings = DevBuildServerSettings.readDevBuildServerSettingsFromIntellijYaml(mainModule.name)
      .takeIf { runContextModule.name != "intellij.clion.main.tests" }  // TODO: remove this after fixing clion tests build types
    val bootstrapClasspath = context.getModuleRuntimeClasspath(module = outputProvider.findRequiredModule("intellij.tools.testsBootstrap"), forTests = false)
      .mapTo(mutableListOf()) { it.toString() }
    @Suppress("NAME_SHADOWING")
    val systemProperties = systemProperties.toMutableMap()
    systemProperties.put("io.netty.allocator.type", "pooled")
    systemProperties.put("test.roots", testRoots.joinToString(File.pathSeparator, transform = toExistingAbsolutePathConverter))
    testPatterns?.let { systemProperties.putIfAbsent("intellij.build.test.patterns", it) }
    testGroups?.let { systemProperties.putIfAbsent("intellij.build.test.groups", it) }
    testTags?.let { systemProperties.putIfAbsent("intellij.build.test.tags", it) }
    systemProperties.putIfAbsent(TestingOptions.PERFORMANCE_TESTS_ONLY_FLAG, options.isPerformanceTestsOnly.toString())
    val allJvmArgs = ArrayList(jvmArgs)
    prepareEnvForTestRun(jvmArgs = allJvmArgs, systemProperties = systemProperties, classPath = bootstrapClasspath, remoteDebugging = remoteDebugging)
    val messages = context.messages
    if (!testPatterns.isNullOrEmpty()) {
      messages.info("Starting tests from patterns '${testPatterns}' from classpath of module '${mainModule.name}'")
    }
    else {
      messages.info("Starting tests from groups '${testGroups}' from classpath of module '${mainModule.name}'")
    }
    if (options.bucketsCount > 1) {
      messages.info("Tests from bucket ${options.bucketIndex + 1} of ${options.bucketsCount} will be executed")
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
      mainModule = mainModule.name,
      systemProperties = systemProperties,
      jvmArgs = allJvmArgs,
      envVariables = envVariables,
      bootstrapClasspath = bootstrapClasspath,
      modulePath = modulePath,
      testClasspath = testClasspath.map(toExistingAbsolutePathConverter),
      devBuildServerSettings = devBuildServerSettings,
    )
    notifySnapshotBuilt(allJvmArgs)
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
      PathManager.PROPERTY_HOME_PATH to context.paths.projectHome.toString(),
      PathManager.PROPERTY_CONFIG_PATH to "$tempDir/config",
      PathManager.PROPERTY_SYSTEM_PATH to "$ideaSystemPath",
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

    for ((key, value) in System.getProperties()) {
      key as String

      if (key.startsWith("pass.")) {
        systemProperties.put(key.substring("pass.".length), value as String)
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
      val utilModule = context.outputProvider.findRequiredModule("intellij.platform.util")
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

    if (BazelRunfiles.isRunningFromBazel) {
      // tests.cmd doesn't call jps-to-bazel and there may be no build/bazel-targets.json file, use it from jps_to_bazel_targets_json rule
      systemProperties.put(ArchivedCompilationContextUtil.BAZEL_TARGETS_JSON_FILE_PROPERTY, ArchivedCompilationContextUtil.getBazelTargetsJsonPath(context.paths.projectHome).absolutePathString())  // resolve against JAVA_RUNFILES or RUNFILES_MANIFEST_FILE
    }
  }

  override suspend fun runTestsSkippedInHeadlessEnvironment() {
    context.compileModules(moduleNames = null, includingTestsInModules = null)
    val tests = spanBuilder("loading all tests annotated with @SkipInHeadlessEnvironment").use { loadTestsSkippedInHeadlessEnvironment() }
    for (it in tests) {
      options.searchScope = JUnitRunConfigurationProperties.TestSearchScope.SINGLE_MODULE.serialized
      options.testPatterns = it.getFirst()
      options.mainModule = it.getSecond()
      runTests()
    }
  }

  private suspend fun loadTestsSkippedInHeadlessEnvironment(): List<Pair<String, String>> {
    val classpath = context.project.modules
      .flatMap { context.getModuleRuntimeClasspath(module = it, forTests = true) }
      .distinct()
    val classloader = UrlClassLoader.build().files(classpath).get()
    @Suppress("UNCHECKED_CAST") val testAnnotation = classloader.loadClass(SkipInHeadlessEnvironment::class.java.name) as Class<out Annotation>
    @Suppress("UNCHECKED_CAST") val ignoreAnnotation = classloader.loadClass(IJIgnore::class.java.name) as Class<out Annotation>

    return context.project.modules.mapConcurrent { module ->
      withContext(CoroutineName("loading tests annotated with @SkipInHeadlessEnvironment from the module '${module.name}'")) {
        val outputRoots = context.outputProvider.getModuleOutputRoots(module, forTests = true)
        if (outputRoots.isEmpty()) return@withContext emptyList()
        val root = requireNotNull(outputRoots.singleOrNull()) { "More than one output root for module '${module.name}': ${outputRoots.joinToString()}" }
        ClassFinder(root, "", false).classes
          .filter {
            val testClass = classloader.loadClass(it)
            !Modifier.isAbstract(testClass.modifiers) &&
            !testClass.isAnnotationPresent(ignoreAnnotation) &&
            testClass.isAnnotationPresent(testAnnotation)
          }
          .map { Pair(it, module.name) }
      }
    }.flatten()
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
    val messages = context.messages
    if (options.testSimplePatterns != null) {
      val exitCode = blockWithDefaultFlowId("running tests w/ simple patterns") {
        runJUnit5Engine(
          mainModule = mainModule,
          systemProperties = systemProperties,
          jvmArgs = jvmArgs,
          envVariables = envVariables,
          bootstrapClasspath = bootstrapClasspath,
          modulePath = modulePath,
          testClasspath = testClasspath,
          suiteName = "__class__",
          methodName = options.testSimplePatterns,
          devBuildSettings = devBuildServerSettings,
        )
      }

      if (exitCode == 1) throw RuntimeException("Tests failed")
      else if (exitCode == NO_TESTS_ERROR) throw NoTestsFound()
      else if (exitCode != 0) throw RuntimeException("Unexpected exit code $exitCode when running tests w/ simple patterns")
    }
    else if (options.isDedicatedTestRuntime != "false") {
      if (options.isDedicatedTestRuntime != "class" && options.isDedicatedTestRuntime != "package") {
        messages.logErrorAndThrow("Unsupported 'intellij.build.test.dedicated.runtime' value: ${options.isDedicatedTestRuntime}. Expected 'class', 'package' or 'false'")
      }
      messages.info("Will run tests in dedicated runtimes ('${options.isDedicatedTestRuntime}')")
      // First, collect all tests for both JUnit5 and JUnit3+4
      val testClasses = blockWithDefaultFlowId("collect tests") {
        val testClassesListFile = Files.createTempFile("tests-to-run-", ".list").apply { Files.delete(this) }
        runJUnit5Engine(
          mainModule = mainModule,
          systemProperties = systemProperties + listOfNotNull(
            "intellij.build.test.list.classes" to testClassesListFile.absolutePathString(),
            when {
              options.shouldSkipJUnit5Tests -> "intellij.build.test.engine.vintage" to "only"
              options.shouldSkipJUnit34Tests -> "intellij.build.test.engine.vintage" to "false"
              else -> null
            },
            "intellij.build.test.ignoreFirstAndLastTests" to "true",
          ),
          jvmArgs = jvmArgs,
          envVariables = envVariables,
          bootstrapClasspath = bootstrapClasspath,
          modulePath = modulePath,
          testClasspath = testClasspath,
          suiteName = "__classpathroot__",
          methodName = null,
          devBuildSettings = null,
        )
        testClassesListFile.let { if (Files.exists(it)) it.readLines() else emptyList() }
      }

      if (testClasses.isEmpty() &&
          // a bucket might be empty for run configurations with too few tests due to imperfect tests balancing
          options.bucketsCount < 2) {
        throw NoTestsFound()
      }

      if (options.isDedicatedTestRuntime == "class") {
        var hasFailures = false

        suspend fun runOneClass(testClassName: String) {
          val exitCode = blockWithDefaultFlowId("running test class '$testClassName'") {
            runJUnit5Engine(
              mainModule = mainModule,
              systemProperties = systemProperties,
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
          if (exitCode == 1) hasFailures = true  // reported as test failure or assertNoUnhandledExceptions if exception
          else if (exitCode == NO_TESTS_ERROR) throw NoTestsFound()
          else if (exitCode != 0) throw RuntimeException("Unexpected exit code $exitCode when running tests in dedicated runtime (class mode)")
        }

        if (testClasses.isNotEmpty()) {
          messages.info("Will run test classes:\n${testClasses.joinToString("\n")}")
          for (s in testClasses) {
            runOneClass(s)
          }
        }

        // On TeamCity test failures themselves control the build status, no need to report them as additional errors
        if (hasFailures && !TeamCityHelper.isUnderTeamCity) {
          throw RuntimeException("Tests failed in dedicated runtime (class mode)")
        }
      }
      else if (options.isDedicatedTestRuntime == "package") {
        var hasFailures = false

        fun groupByPackages(tests: List<String>): Map<String, List<String>> {
          return tests.groupBy {
            val i = it.lastIndexOf('.')
            if (i != -1) it.substring(0, i) else ""
          }
        }

        suspend fun runOnePackage(entry: Map.Entry<String, List<String>>) {
          val packageName = entry.key
          val classes = entry.value

          val exitCode = blockWithDefaultFlowId("running tests in package '$packageName'") {
            runJUnit5Engine(
              mainModule = mainModule,
              systemProperties = systemProperties,
              jvmArgs = jvmArgs,
              envVariables = envVariables,
              bootstrapClasspath = bootstrapClasspath,
              modulePath = modulePath,
              testClasspath = testClasspath,
              suiteName = "__class__",
              methodName = classes.joinToString(";"),
              devBuildSettings = devBuildServerSettings,
            )
          }
          if (exitCode == 1) hasFailures = true  // reported as test failure or assertNoUnhandledExceptions if exception
          else if (exitCode == NO_TESTS_ERROR) throw NoTestsFound()
          else if (exitCode != 0) throw RuntimeException("Unexpected exit code $exitCode when running tests in dedicated runtime (package mode)")
        }

        if (testClasses.isNotEmpty()) {
          val packages = groupByPackages(testClasses)
          messages.info(packages.entries.joinToString(prefix = "Will run tests in packages:\n", separator = "\n") { e ->
            e.value.joinToString(prefix = "${e.key}\n  ", separator = "\n  ")
          })
          for (entry in packages) {
            runOnePackage(entry)
          }
        }

        // On TeamCity test failures themselves control the build status, no need to report them as additional errors
        if (hasFailures && !TeamCityHelper.isUnderTeamCity) {
          throw RuntimeException("Tests failed in dedicated runtime (package mode)")
        }
      }
    }
    else {
      if (options.repeatCount > 1) {
        messages.info("Will run selected tests ${options.repeatCount} times")
      }
      var hadAnyFailures = false

      for (runNumber in 1..options.repeatCount) {
        val additionalProperties = mutableMapOf<String, String>()

        // save failed tests to retry
        val failedClassesListFile = if (options.attemptCount > 1) Files.createTempFile("failed-classes-", ".list").apply { Files.delete(this) } else null
        failedClassesListFile?.let { additionalProperties["intellij.build.test.retries.failedClasses.file"] = it.absolutePathString() }
        var failedClasses: List<String>? = null

        var exitCode = 0

        for (attempt in 1..options.attemptCount) {
          val spanNameSuffix = buildString {
            if (options.repeatCount > 1) {
              append(" (run $runNumber/${options.repeatCount})")
            }
            if (options.attemptCount > 1) {
              append(" (attempt $attempt)")
            }
          }

          if (attempt > 1) {
            additionalProperties["intellij.build.test.ignoreFirstAndLastTests"] = "true"
            check(!failedClasses.isNullOrEmpty())  // already checked in the previous attempt
            additionalProperties["intellij.build.test.patterns"] = failedClasses.joinToString(";")
          }

          blockWithDefaultFlowId("run tests${spanNameSuffix}") {
            exitCode = runJUnit5Engine(
              mainModule = mainModule,
              systemProperties = systemProperties + additionalProperties + listOfNotNull(
                when {
                  options.shouldSkipJUnit5Tests -> "intellij.build.test.engine.vintage" to "only"
                  options.shouldSkipJUnit34Tests -> "intellij.build.test.engine.vintage" to "false"
                  else -> null
                },
              ),
              jvmArgs = jvmArgs,
              envVariables = envVariables,
              bootstrapClasspath = bootstrapClasspath,
              modulePath = modulePath,
              testClasspath = testClasspath,
              suiteName = "__classpathroot__",
              methodName = null,
              devBuildSettings = devBuildServerSettings,
            )
            failedClasses = failedClassesListFile?.let { if (Files.exists(it)) it.readLines() else emptyList() }
            if (failedClassesListFile != null) Files.deleteIfExists(failedClassesListFile)
          }

          if (exitCode == NO_TESTS_ERROR &&
              // only check on the first full run
              attempt == 1 &&
              runNumber == 1 &&
              // a bucket might be empty for run configurations with too few tests due to imperfect tests balancing
              options.bucketsCount < 2) {
            throw NoTestsFound()
          }
          if (exitCode != 0 && exitCode != 1 && exitCode != NO_TESTS_ERROR) {
            throw RuntimeException("Unexpected exit code $exitCode when running tests")
          }

          if (options.attemptCount > 1) {
            if (failedClasses!!.isNotEmpty()) {
              if (exitCode != 1) throw RuntimeException("Unexpected exit code $exitCode when running tests but found failed tests to retry")
              messages.warning("Will rerun tests: $failedClasses")
            }
            else {
              if (exitCode == 1) throw RuntimeException("Unexpected exit code $exitCode when running tests but no failed tests to retry found")
            }
          }
        }

        val hadRunFailures = exitCode == 1
        hadAnyFailures = hadAnyFailures || hadRunFailures

        // On TeamCity test failures themselves control the build status, no need to report them as additional errors
        if (hadRunFailures && !TeamCityHelper.isUnderTeamCity) {
          val runSuffix = if (options.repeatCount > 1) " on run $runNumber/${options.repeatCount}" else ""
          throw RuntimeException("Tests failed$runSuffix (exit code: $exitCode, $NO_TESTS_ERROR means no tests found)")
        }
      }

      if (!TeamCityHelper.isUnderTeamCity && !hadAnyFailures) {
        println("*** All tests passed ***")
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
    suiteName: String,
    methodName: String?,
    devBuildSettings: DevBuildServerSettings?,
  ): Int {
    val useDevMode = devBuildSettings != null && devBuildSettings.mainClass.isNotEmpty()
    if (useDevMode) {
      val bootClasspath = context.getModuleRuntimeClasspath(module = context.outputProvider.findRequiredModule(IJENT_BOOT_CLASSPATH_MODULE), forTests = false)
      val classpath = context.getModuleRuntimeClasspath(module = context.outputProvider.findRequiredModule(devBuildSettings.mainClassModule), forTests = false)
        .filter { !bootClasspath.contains(it) }
        .map { it.toString() }

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

      if (options.isDedicatedTestRuntime != "false" || suiteName == null || suiteName == "__class__" || suiteName == "__classpathroot__") {
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
    suiteName: String,
    methodName: String?,
    devBuildModeSettings: DevBuildServerSettings?,
  ): Int {
    val args = ArrayList<String>()
    args.add("-classpath")
    args.add(classpath.joinToString(separator = File.pathSeparator))

    /*
    TODO it's better to load byte buddy beforehand and prohibit dynamic agent loading
    WARNING: A Java agent has been loaded dynamically (/var/folders/y2/wzcbjbb16rz5l119wsct9vwc0000gn/T/byteBuddyAgent3573542851707188859.jar)
    WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
    WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
    WARNING: Dynamic loading of agents will be disallowed by default in a future release
     */
    args.add("-XX:+EnableDynamicAgentLoading")

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

    val mainClass = "com.intellij.tests.JUnit5TeamCityRunner"
    if (devBuildModeSettings == null) {
      args.add(mainClass)
    }
    else {
      devBuildModeSettings.apply(mainClass, mainModule, args, environment)
    }

    args.add(suiteName)

    if (methodName != null) {
      args.add(methodName)
    }

    val argFile = CommandLineWrapperUtil.createArgumentFile(args, Charset.defaultCharset())
    val runtime = getRuntimeExecutablePath().toString()

    context.messages.info("Starting tests on runtime $runtime")
    val builder = ProcessBuilder(runtime, "@" + argFile.absolutePath).apply {
      removeBazelEnvironmentVariables(environment())  // to prevent treating the test process as BazelRunfiles#isRunningFromBazel
    }
    builder.environment().putAll(environment)
    builder.inheritIO()
    val exitCode = builder.start().awaitExit()
    if (exitCode != 0 && exitCode != NO_TESTS_ERROR) {
      context.messages.warning("Tests failed with exit code $exitCode")
    }
    return exitCode
  }
}

private fun appendJUnitStarter(classPath: MutableList<String>, context: CompilationContext) {
  for ((libName, moduleName) in arrayOf("JUnit5" to null, "JUnit5Launcher" to null, "JUnit5Vintage" to "intellij.libraries.junit5.vintage", "JUnit5Jupiter" to null)) {
    for (library in context.outputProvider.findLibraryRoots(libName, moduleName)) {
      classPath.add(library.toString())
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
  "-D${PathManager.PROPERTY_HOME_PATH}=",
  "-D${PathManager.PROPERTY_CONFIG_PATH}=",
  "-D${PathManager.PROPERTY_SYSTEM_PATH}=",
  "-D${PathManager.PROPERTY_LOG_PATH}=",
)

private fun removeStandardJvmOptions(vmOptions: List<String>): List<String> {
  return vmOptions.filter { option -> ignoredPrefixes.none(option::startsWith) }
}

private fun removeBazelEnvironmentVariables(environment: MutableMap<String, String>) = listOf(
  "BUILD_WORKING_DIRECTORY",
  "BUILD_WORKSPACE_DIRECTORY",
  "JAVA_RUNFILES",
  "RUNFILES_DIR",
  "RUNFILES_MANIFEST_FILE",
  "RUNFILES_MANIFEST_ONLY",
  "SELF_LOCATION",
  "TEST_SRCDIR",
  "TEST_TMPDIR",
).forEach { environment.remove(it) }

private suspend inline fun <T> blockWithDefaultFlowId(
  name: String,
  crossinline operation: suspend CoroutineScope.(Span) -> T,
): T {
  // the test process inherits I/O from the current process and writes to stdout/stderr w/o flowId, start a new block in the root flow to capture it
  if (TeamCityHelper.isUnderTeamCity) {
    TraceManager.flush()  // before BlockOpened
    println(BlockOpened(name))
  }
  try {
    // SpanAwareServiceMessage uses SpanContext#getSpanId as flowId, don't use SpanKt#block to preserve the default flow ID
    return spanBuilder(name).use(operation = operation)
  }
  finally {
    if (TeamCityHelper.isUnderTeamCity) {
      TraceManager.flush()  // before BlockClosed
      println(BlockClosed(name))
    }
  }
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

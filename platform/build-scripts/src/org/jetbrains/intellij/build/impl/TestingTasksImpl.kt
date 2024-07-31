// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import org.jetbrains.intellij.build.telemetry.use
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.causal.CausalProfilingOptions
import org.jetbrains.intellij.build.io.runProcess
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.java.ModulePathSplitter
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.lang.reflect.Modifier
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.regex.Pattern
import java.util.stream.Stream
import kotlin.io.path.absolutePathString
import kotlin.io.path.readLines

internal class TestingTasksImpl(context: CompilationContext, private val options: TestingOptions) : TestingTasks {
  private val context: CompilationContext = if (options.useArchivedCompiledClasses) ArchivedCompilationContext(context) else context
  private val NO_TESTS_ERROR = 42

  private fun loadRunConfigurations(name: String): List<JUnitRunConfigurationProperties> {
    return try {
      val projectHome = context.paths.projectHome
      val file = RunConfigurationProperties.findRunConfiguration(projectHome, name)
      val configuration = RunConfigurationProperties.getConfiguration(file)
      when (val type = RunConfigurationProperties.getConfigurationType(configuration)) {
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
    catch (e: Exception) {
      val description = e.message?.lineSequence()?.firstOrNull() ?: ""
      context.messages.reportBuildProblem(description, identity = name)
      emptyList()
    }
  }

  private fun loadTestRunConfigurations(): List<JUnitRunConfigurationProperties>? {
    val testConfigurationsOption = options.testConfigurations ?: return null
    return testConfigurationsOption
      .splitToSequence(';')
      .filter(String::isNotEmpty)
      .flatMap(::loadRunConfigurations)
      .toList()
  }

  override suspend fun runTests(additionalJvmOptions: List<String>,
                                additionalSystemProperties: Map<String, String>,
                                defaultMainModule: String?,
                                rootExcludeCondition: ((Path) -> Boolean)?) {
    if (options.isTestDiscoveryEnabled && options.isPerformanceTestsOnly) {
      context.messages.buildStatus("Skipping performance testing with Test Discovery, {build.status.text}")
      return
    }

    val mainModule = options.mainModule ?: defaultMainModule
    checkOptions(mainModule)

    val runConfigurations = loadTestRunConfigurations()

    try {
      val compilationTasks = CompilationTasks.create(context)
      options.beforeRunProjectArtifacts?.splitToSequence(';')?.filterNotTo(HashSet(), String::isEmpty)?.let {
        compilationTasks.buildProjectArtifacts(it)
      }

      if (runConfigurations != null) {
        compilationTasks.compileModules(listOf("intellij.tools.testsBootstrap"),
                                        listOf("intellij.platform.buildScripts") + runConfigurations.map { it.moduleName })
        compilationTasks.buildProjectArtifacts(runConfigurations.flatMapTo(LinkedHashSet()) { it.requiredArtifacts })
      }
      else {
        compilationTasks.compileModules(listOf("intellij.tools.testsBootstrap"),
                                        listOfNotNull(mainModule, "intellij.platform.buildScripts"))
      }
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
      val systemProperties = LinkedHashMap<String, String>(additionalSystemProperties)
      val effectiveAdditionalJvmOptions = additionalJvmOptions.toMutableList()
      loadTestDiscovery(effectiveAdditionalJvmOptions, systemProperties)
      if (runConfigurations == null) {
        runTestsFromGroupsAndPatterns(effectiveAdditionalJvmOptions, checkNotNull(mainModule) {
          "Main module is not specified"
        }, rootExcludeCondition, systemProperties)
      }
      else {
        runTestsFromRunConfigurations(effectiveAdditionalJvmOptions, runConfigurations, systemProperties)
      }
      if (options.isTestDiscoveryEnabled) {
        publishTestDiscovery(context.messages, testDiscoveryTraceFilePath)
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
      if (mainModule != null) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.main.module")
      }
    }
    else if (options.testPatterns != null && options.testGroups != TestingOptions.ALL_EXCLUDE_DEFINED_GROUP) {
      warnOptionIgnored("intellij.build.test.patterns", "intellij.build.test.groups")
    }
    if (options.batchTestIncludes != null && !isRunningInBatchMode) {
      context.messages.warning(
        "'intellij.build.test.batchTest.includes' option will be ignored as other tests matching options are specified.")
    }
  }

  private fun warnOptionIgnored(specifiedOption: String, ignoredOption: String) {
    context.messages.warning("'${specifiedOption}' option is specified, so '${ignoredOption}' will be ignored.")
  }

  private fun runTestsFromRunConfigurations(additionalJvmOptions: List<String>,
                                            runConfigurations: List<JUnitRunConfigurationProperties>,
                                            systemProperties: MutableMap<String, String>) {
    for (configuration in runConfigurations) {
      blockAndSpan("run '${configuration.name}' run configuration") {
        runTestsFromRunConfiguration(configuration, additionalJvmOptions, systemProperties)
      }
    }
  }

  private fun runTestsFromRunConfiguration(runConfigurationProperties: JUnitRunConfigurationProperties,
                                           additionalJvmOptions: List<String>,
                                           systemProperties: Map<String, String>) {
    if (runConfigurationProperties.testSearchScope != JUnitRunConfigurationProperties.TestSearchScope.WHOLE_PROJECT) {
      context.messages.warning(
        "Run configuration '${runConfigurationProperties.name}' uses test search scope '${runConfigurationProperties.testSearchScope.serialized}', " +
        "while only '${JUnitRunConfigurationProperties.TestSearchScope.WHOLE_PROJECT.serialized}' is supported. Scope will be ignored")
    }
    try {
      runTestsProcess(mainModule = runConfigurationProperties.moduleName,
                      testGroups = null,
                      testPatterns = runConfigurationProperties.testClassPatterns.joinToString(separator = ";"),
                      jvmArgs = removeStandardJvmOptions(runConfigurationProperties.vmParameters) + additionalJvmOptions,
                      systemProperties = systemProperties,
                      envVariables = runConfigurationProperties.envVariables,
                      remoteDebugging = false)
    }
    catch (e: NoTestsFound) {
      throw RuntimeException("No tests were found in the configuration '${runConfigurationProperties.name}'").apply {
        addSuppressed(e)
      }
    }
  }

  private fun runTestsFromGroupsAndPatterns(additionalJvmOptions: List<String>,
                                            mainModule: String,
                                            rootExcludeCondition: ((Path) -> Boolean)?,
                                            systemProperties: MutableMap<String, String>) {
    if (rootExcludeCondition != null) {
      val excludedRootPaths = ArrayList<Path>(context.project.modules.size * 2)
      for (module in context.project.modules) {
        val contentRoots = module.contentRootsList.urls
        if (!contentRoots.isEmpty() && rootExcludeCondition(Path.of(JpsPathUtil.urlToPath(contentRoots.first())))) {
          excludedRootPaths.add(context.getModuleOutputDir(module))
          excludedRootPaths.add(context.getModuleTestsOutputDir(module))
        }
      }
      val excludedRoots = excludedRootPaths.replaceWithArchivedIfNeededLP().filter(Files::exists).map(Path::toString)

      val excludedRootsFile = context.paths.tempDir.resolve("excluded.classpath")
      Files.createDirectories(excludedRootsFile.parent)
      Files.writeString(excludedRootsFile, excludedRoots.joinToString(separator = "\n"))
      systemProperties["exclude.tests.roots.file"] = excludedRootsFile.toString()
    }

    try {
      runTestsProcess(mainModule = mainModule,
                      testGroups = options.testGroups,
                      testPatterns = options.testPatterns,
                      jvmArgs = additionalJvmOptions,
                      systemProperties = systemProperties,
                      remoteDebugging = false)
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
    if (!options.isTestDiscoveryEnabled) {
      return
    }

    val testDiscovery = "intellij-test-discovery"
    val library = context.projectModel.project.libraryCollection.findLibrary(testDiscovery)
                  ?: throw RuntimeException("Can't find the ${testDiscovery} library, but test discovery capturing enabled.")

    val agentJar = library.getPaths(JpsOrderRootType.COMPILED)
                     .firstOrNull {
                       val name = it.fileName.toString()
                       name.startsWith("intellij-test-discovery") && name.endsWith(".jar")
                     } ?: throw RuntimeException("Can't find the agent in ${testDiscovery} library, but test discovery capturing enabled.")

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

    systemProperties["test.discovery.excluded.roots"] = excludeRoots.joinToString(separator = ";")
  }

  private val testDiscoveryTraceFilePath: String
    get() = options.testDiscoveryTraceFilePath ?: context.paths.projectHome.resolve("intellij-tracing/td.tr").toString()

  private fun debugTests(remoteDebugJvmOptions: String,
                         additionalJvmOptions: List<String>,
                         mainModule: String) {
    val testConfigurationType = System.getProperty("teamcity.remote-debug.type")
    if (testConfigurationType != "junit") {
      context.messages.error(
        "Remote debugging is supported for junit run configurations only, but 'teamcity.remote-debug.type' is ${testConfigurationType}")
    }
    val testObject = System.getProperty("teamcity.remote-debug.junit.type")
    val junitClass = System.getProperty("teamcity.remote-debug.junit.class")
    if (testObject != "class") {
      val message = "Remote debugging supports debugging all test methods in a class for now, debugging isn't supported for '${testObject}'"
      if (testObject == "method") {
        context.messages.warning(message)
        context.messages.warning("Launching all test methods in the class ${junitClass}")
      }
      else {
        context.messages.error(message)
      }
    }
    if (junitClass == null) {
      context.messages.error("Remote debugging supports debugging all test methods in a class for now, but target class isn't specified")
    }
    if (options.testPatterns != null) {
      context.messages.warning("'intellij.build.test.patterns' option is ignored while debugging via TeamCity plugin")
    }
    if (options.testConfigurations != null) {
      context.messages.warning("'intellij.build.test.configurations' option is ignored while debugging via TeamCity plugin")
    }
    runTestsProcess(mainModule = mainModule,
                    testGroups = null,
                    testPatterns = junitClass,
                    jvmArgs = removeStandardJvmOptions(StringUtilRt.splitHonorQuotes(remoteDebugJvmOptions, ' ')) + additionalJvmOptions,
                    systemProperties = emptyMap(),
                    remoteDebugging = true)
  }

  private fun runTestsProcess(mainModule: String,
                              testGroups: String?,
                              testPatterns: String?,
                              jvmArgs: List<String>,
                              systemProperties: Map<String, String>,
                              envVariables: Map<String, String> = emptyMap(),
                              remoteDebugging: Boolean) {
    val useKotlinK2 = System.getProperty("idea.kotlin.plugin.use.k2", "false").toBoolean() ||
                      System.getProperty("teamcity.buildType.id", "").contains("KotlinK2Tests")
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
      val testFrameworkOutput = context.getModuleOutputDir(testFrameworkCoreModule).toFile()
      if (!testRoots.contains(testFrameworkOutput)) {
        testRoots.addAll(context.getModuleRuntimeClasspath(testFrameworkCoreModule, false).map(::File))
      }
    }

    val testClasspath: List<String>
    val modulePath: List<String>?

    val moduleInfoFile = JpsJavaExtensionService.getInstance().getJavaModuleIndex(context.project).getModuleInfoFile(mainJpsModule, true)
    val toExistingAbsolutePathConverter: (File) -> String? = { if (it.exists()) it.absolutePath else null }
    if (moduleInfoFile != null) {
      val outputDir = ModuleBuildTarget(mainJpsModule, JavaModuleBuildTargetType.TEST).outputDir
      val pair = ModulePathSplitter().splitPath(moduleInfoFile, mutableSetOf(outputDir), HashSet(testRoots))
      modulePath = pair.first.path.toList().replaceWithArchivedIfNeededLF().mapNotNull(toExistingAbsolutePathConverter)
      testClasspath = pair.second.toList().replaceWithArchivedIfNeededLF().mapNotNull(toExistingAbsolutePathConverter)
    }
    else {
      modulePath = null
      testClasspath = testRoots.replaceWithArchivedIfNeededLF().mapNotNull(toExistingAbsolutePathConverter)
    }
    val bootstrapClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule("intellij.tools.testsBootstrap"), false)
      .toMutableList()
    val classpathFile = context.paths.tempDir.resolve("junit.classpath")
    Files.createDirectories(classpathFile.parent)
    // this is required to collect tests both on class and module paths
    Files.writeString(classpathFile, testRoots.replaceWithArchivedIfNeededLF().mapNotNull(toExistingAbsolutePathConverter).joinToString(separator = "\n"))
    @Suppress("NAME_SHADOWING")
    val systemProperties = systemProperties.toMutableMap()
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
      messages.info("Running tests from ${mainModule} matched by '${options.batchTestIncludes}' pattern.")
    }
    else {
      messages.info("Starting tests from groups '${testGroups}' from classpath of module '${mainModule}'")
    }
    if (options.bucketsCount > 1) {
      messages.info("Tests from bucket ${options.bucketIndex} of ${options.bucketsCount} will be executed")
    }
    messages.block("Test classpath and runtime info") {
      runBlocking(Dispatchers.IO) {
        val runtime = getRuntimeExecutablePath().toString()
        messages.info("Runtime: ${runtime}")
        runProcess(args = listOf(runtime, "-version"), inheritOut = true, inheritErrToOut = true)
      }
      messages.info("Runtime options: ${allJvmArgs}")
      messages.info("System properties: ${systemProperties}")
      messages.info("Bootstrap classpath: ${bootstrapClasspath}")
      messages.info("Tests classpath: ${testClasspath}")
      modulePath?.let { mp ->
        @Suppress("SpellCheckingInspection")
        messages.info("Tests modulepath: $mp")
      }
      if (!envVariables.isEmpty()) {
        messages.info("Environment variables: ${envVariables}")
      }
    }
    runJUnit5Engine(mainModule = mainModule,
                    systemProperties = systemProperties,
                    jvmArgs = allJvmArgs,
                    envVariables = envVariables,
                    bootstrapClasspath = bootstrapClasspath,
                    modulePath = modulePath,
                    testClasspath = testClasspath)
    notifySnapshotBuilt(allJvmArgs)
  }

  private fun List<File>.replaceWithArchivedIfNeededLF(): List<File> {
    if (context is ArchivedCompilationContext) {
      return context.replaceWithCompressedIfNeededLF(this)
    }
    return this
  }

  private fun List<Path>.replaceWithArchivedIfNeededLP(): List<Path> {
    if (context is ArchivedCompilationContext) {
      return context.replaceWithCompressedIfNeededLP(this)
    }
    return this
  }

  private suspend fun getRuntimeExecutablePath(): Path {
    val runtimeDir: Path
    if (options.customRuntimePath != null) {
      runtimeDir = Path.of(checkNotNull(options.customRuntimePath))
      check(Files.isDirectory(runtimeDir)) {
        "Custom Jre path from system property '${TestingOptions.TEST_JRE_PROPERTY}' is missing: ${runtimeDir}"
      }
    }
    else {
      runtimeDir = context.bundledRuntime.getHomeForCurrentOsAndArch()
    }

    var java = runtimeDir.resolve(if (SystemInfoRt.isWindows) "bin/java.exe" else "bin/java")
    if (SystemInfoRt.isMac && Files.notExists(java)) {
      java = runtimeDir.resolve("Contents/Home/bin/java")
    }
    check(Files.exists(java)) { "java executable is missing: ${java}" }
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
    NioFiles.deleteRecursively(snapshotsDir)
    Files.createDirectories(snapshotsDir)
    return snapshotsDir
  }

  override fun prepareEnvForTestRun(jvmArgs: MutableList<String>,
                                    systemProperties: MutableMap<String, String>,
                                    classPath: MutableList<String>,
                                    remoteDebugging: Boolean,
                                    cleanSystemDir: Boolean) {
    val snapshotsDir = createSnapshotsDirectory()
    val hprofSnapshotFilePath = snapshotsDir.resolve("intellij-tests-oom.hprof").toString()
    jvmArgs.addAll(0, listOf("-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=${hprofSnapshotFilePath}"))

    val customMemoryOptions = options.jvmMemoryOptions?.trim()?.split(Regex("\\s+"))?.takeIf { it.isNotEmpty() }
    jvmArgs.addAll(
      index = 0,
      elements = computeVmOptions(
        isEAP = true,
        bundledRuntime = context.bundledRuntime,
        customJvmMemoryOptions = if (customMemoryOptions == null) mapOf("-Xms" to "750m", "-Xmx" to "1024m") else emptyMap(),
        additionalVmOptions = customMemoryOptions,
      ),
    )

    val tempDir = System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir"))
    val ideaSystemPath = Path.of("$tempDir/system")
    if (cleanSystemDir) {
      spanBuilder("idea.system.path cleanup").use {
        NioFiles.deleteRecursively(ideaSystemPath)
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
      "teamcity.build.branch.is_default" to System.getProperty("teamcity.build.branch.is_default"),
      "jna.nosys" to "true",
      "javax.xml.parsers.SAXParserFactory" to "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
      "file.encoding" to "UTF-8",
      "io.netty.leakDetectionLevel" to "PARANOID",
      "kotlinx.coroutines.debug" to "on",
      "sun.io.useCanonCaches" to "false",
    )) {
      if (v != null) {
        systemProperties.putIfAbsent(k, v)
      }
    }

    System.getProperties().forEach { key, value ->
      key as String

      if (key.startsWith("pass.")) {
        systemProperties[key.substring("pass.".length)] = value as String
      }

      /**
       * Make test inherit Maven dependency resolver settings
       * See [org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder]
       */

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
      jvmArgs.addAll(buildCausalProfilingAgentJvmArg(causalProfilingOptions))
    }

    jvmArgs.addAll(getCommandLineArgumentsForOpenPackages(context))

    if (suspendDebugProcess) {
      context.messages.info("""
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
        systemProperties.compute("vfs.additional-allowed-roots") { _, old -> if (old == null) it else "$it:$old" }
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
    CompilationTasks.create(context).compileAllModulesAndTests()
    val tests = spanBuilder("loading all tests annotated with @SkipInHeadlessEnvironment").use { loadTestsSkippedInHeadlessEnvironment() }
    for (it in tests) {
      options.batchTestIncludes = it.getFirst()
      options.mainModule = it.getSecond()
      runTests()
    }
  }

  private fun loadTestsSkippedInHeadlessEnvironment(): List<Pair<String, String>> {
    val classpath = context.project.modules.asSequence()
      .flatMap { context.getModuleRuntimeClasspath(module = it, forTests = true) }
      .distinct()
      .map { Path.of(it) }
      .toList()
    val classloader = UrlClassLoader.build().files(classpath).get()
    val testAnnotation = classloader.loadClass("com.intellij.testFramework.SkipInHeadlessEnvironment")
    return context.project.modules.parallelStream()
      .flatMap { module ->
        val root = context.getModuleTestsOutputDir(module)
        if (Files.exists(root)) {
          @Suppress("SSBasedInspection")
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
              .map { Pair(it, module.name) }.toList()
          }.stream()
        }
        else {
          Stream.empty()
        }
      }
      .toList()
  }

  private fun getTestClassesForModule(mainModule: String, filteringPattern: Pattern = Pattern.compile(".*\\.class")): List<String> {
    val root = context.getModuleTestsOutputDir(context.findRequiredModule(mainModule))
    val testClasses = Files.walk(root).use { stream ->
      stream.map { FileUtilRt.toSystemIndependentName(root.relativize(it).toString()) }.filter {
        filteringPattern.matcher(it).matches()
      }.toList()
    } ?: listOf()

    if (testClasses.isEmpty()) {
      throw RuntimeException("No tests were found in ${root} with ${filteringPattern}")
    }

    return testClasses
  }

  private fun runInBatchMode(mainModule: String,
                             systemProperties: Map<String, String>,
                             jvmArgs: List<String>,
                             envVariables: Map<String, String>,
                             bootstrapClasspath: List<String>,
                             testClasspath: List<String>) {
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
          val exitCode = runJUnit5Engine(systemProperties = systemProperties,
                                         jvmArgs = jvmArgs,
                                         envVariables = envVariables,
                                         bootstrapClasspath = bootstrapClasspath,
                                         modulePath = null,
                                         testClasspath = testClasspath,
                                         suiteName = qName,
                                         methodName = null)
          noTests = exitCode == NO_TESTS_ERROR
        }
        // Run JUnit 4 and 5 test methods separately if any
        else if (jUnit4And5TestMethods.isNotEmpty()) {
          for (method in jUnit4And5TestMethods) {
            val exitCode = runJUnit5Engine(
              systemProperties, jvmArgs, envVariables, bootstrapClasspath, null, testClasspath,
              qName, method)
            noTests = noTests && exitCode == NO_TESTS_ERROR
          }
        }

        // Fallback to running whole class (JUnit 3+4)
        if (noTests) {
          val exitCode = runJUnit5Engine(
            systemProperties, jvmArgs, envVariables, bootstrapClasspath, null, testClasspath,
            qName, null,
          )
          noTests = exitCode == NO_TESTS_ERROR
        }
        noTestsInAllClasses = noTestsInAllClasses && noTests
      }
      catch (e: Throwable) {
        throw RuntimeException("Failed to process ${qName}", e)
      }
    }

    if (noTestsInAllClasses) {
      throw RuntimeException("No tests were found in ${mainModule} with ${pattern}")
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

  private fun <T> blockAndSpan(spanName: String, task: Callable<T>): T {
    var result: T? = null
    context.messages.block(spanName) {
      spanBuilder(spanName).use {
        result = task.call()
      }
    }
    return result!!
  }

  private fun runJUnit5Engine(mainModule: String,
                              systemProperties: Map<String, String>,
                              jvmArgs: List<String>,
                              envVariables: Map<String, String>,
                              bootstrapClasspath: List<String>,
                              modulePath: List<String>?,
                              testClasspath: List<String>) {
    if (isRunningInBatchMode) {
      spanBuilder("run tests in batch mode")
        .setAttribute(AttributeKey.stringKey("pattern"), options.batchTestIncludes ?: "")
        .use {
          runInBatchMode(mainModule, systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath)
        }
    }
    else if (options.isDedicatedTestRuntime != "false") {
      if (options.isDedicatedTestRuntime != "class" && options.isDedicatedTestRuntime != "package") {
        context.messages.error("Unsupported 'intellij.build.test.dedicated.runtime' value: ${options.isDedicatedTestRuntime}. Expected 'class', 'package' or 'false'")
      }
      context.messages.info("Will run tests in dedicated runtimes ('${options.isDedicatedTestRuntime}')")
      // First, collect all tests for both JUnit5 and JUnit3+4
      val testClassesJUnit5 = blockAndSpan("collect junit 5 tests") {
        val testClassesListFile = Files.createTempFile("tests-to-run-", ".list").apply { Files.delete(this) }
        runJUnit5Engine(
          systemProperties = systemProperties + ("intellij.build.test.list.classes" to testClassesListFile.absolutePathString()),
          jvmArgs = jvmArgs,
          envVariables = envVariables,
          bootstrapClasspath = bootstrapClasspath,
          modulePath = modulePath,
          testClasspath = testClasspath,
          suiteName = null,
          methodName = null)
        return@blockAndSpan testClassesListFile.let { if (Files.exists(it)) it.readLines() else emptyList() }
      }
      val testClassesJUnit34 = blockAndSpan("collect junit 3+4 tests") {
        val testClassesListFile = Files.createTempFile("tests-to-run-", ".list").apply { Files.delete(this) }
        runJUnit5Engine(
          systemProperties = systemProperties + ("intellij.build.test.list.classes" to testClassesListFile.absolutePathString()),
          jvmArgs = jvmArgs,
          envVariables = envVariables,
          bootstrapClasspath = bootstrapClasspath,
          modulePath = modulePath,
          testClasspath = testClasspath,
          suiteName = options.bootstrapSuite,
          methodName = null)
        return@blockAndSpan testClassesListFile.let { if (Files.exists(it)) it.readLines() else emptyList() }
      }

      if (testClassesJUnit5.isEmpty() && testClassesJUnit34.isEmpty() &&
          // a bucket might be empty for run configurations with too few tests due to imperfect tests balancing
          options.bucketsCount < 2) {
        throw NoTestsFound()
      }

      if (options.isDedicatedTestRuntime == "class") {
        fun runOneClass(testClassName: String) {
          val exitCode = blockAndSpan("running test class '$testClassName'") {
            runJUnit5Engine(
              systemProperties = systemProperties + ("idea.performance.tests.discovery.filter" to "true"),
              jvmArgs = jvmArgs,
              envVariables = envVariables,
              bootstrapClasspath = bootstrapClasspath,
              modulePath = modulePath,
              testClasspath = testClasspath,
              suiteName = testClassName,
              methodName = null
            )
          }
          if (exitCode == NO_TESTS_ERROR) throw NoTestsFound()
        }

        if (testClassesJUnit5.isNotEmpty()) {
          context.messages.info("Will run JUnit 5 tests:\n${testClassesJUnit5.joinToString("\n")}")
          testClassesJUnit5.forEach(::runOneClass)
        }
        if (testClassesJUnit34.isNotEmpty()) {
          context.messages.info("Will run JUnit 3+4 tests:\n${testClassesJUnit34.joinToString("\n")}")
          testClassesJUnit34.forEach(::runOneClass)
        }
      }
      else if (options.isDedicatedTestRuntime == "package") {
        fun groupByPackages(tests: List<String>): Map<String, List<String>> {
          return tests.groupBy {
            val i = it.lastIndexOf('.')
            if (i != -1) it.substring(0, i) else ""
          }
        }
        fun runOnePackage(entry: Map.Entry<String, List<String>>) {
          val packageName = entry.key
          val classes = entry.value

          val exitCode = blockAndSpan("running tests in package '$packageName'") {
            runJUnit5Engine(
              systemProperties = systemProperties + ("idea.performance.tests.discovery.filter" to "true"),
              jvmArgs = jvmArgs,
              envVariables = envVariables,
              bootstrapClasspath = bootstrapClasspath,
              modulePath = modulePath,
              testClasspath = testClasspath,
              suiteName = "__classes__",
              methodName = classes.joinToString(";")
            )
          }
          if (exitCode == NO_TESTS_ERROR) throw NoTestsFound()
        }

        if (testClassesJUnit5.isNotEmpty()) {
          val packages = groupByPackages(testClassesJUnit5)
          context.messages.info(packages.entries.joinToString(prefix = "Will run JUnit 5 packages:\n", separator = "\n") { e ->
            e.value.joinToString(prefix = "${e.key}\n  ", separator = "\n  ")
          })
          packages.forEach(::runOnePackage)
        }
        if (testClassesJUnit34.isNotEmpty()) {
          val packages = groupByPackages(testClassesJUnit34)
          context.messages.info(packages.entries.joinToString(prefix = "Will run JUnit 3+4 packages:\n", separator = "\n") { e ->
            e.value.joinToString(prefix = "${e.key}\n  ", separator = "\n  ")
          })
          packages.forEach(::runOnePackage)
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
      var runJUnit5 = true
      var runJUnit34 = true
      for (attempt in 1..options.attemptCount) {
        if (!runJUnit5 && !runJUnit34) break
        val spanNameSuffix = if (options.attemptCount > 1) " (attempt $attempt)" else ""
        val additionalProperties: Map<String, String> = if (attempt > 1) mapOf("intellij.build.test.ignoreFirstAndLastTests" to "true") else emptyMap()

        val exitCode5: Int = if (runJUnit5) blockAndSpan("run junit 5 tests${spanNameSuffix}") {
          runJUnit5Engine(
            systemProperties = systemProperties + additionalProperties + additionalPropertiesJUnit5,
            jvmArgs = jvmArgs,
            envVariables = envVariables,
            bootstrapClasspath = bootstrapClasspath,
            modulePath = modulePath,
            testClasspath = testClasspath,
            suiteName = null,
            methodName = null
          )
        }
        else 0

        val exitCode34: Int = if (runJUnit34) blockAndSpan("run junit 3+4 tests${spanNameSuffix}") {
          runJUnit5Engine(
            systemProperties = systemProperties + additionalProperties + additionalPropertiesJUnit34,
            jvmArgs = jvmArgs,
            envVariables = envVariables,
            bootstrapClasspath = bootstrapClasspath,
            modulePath = modulePath,
            testClasspath = testClasspath,
            suiteName = options.bootstrapSuite,
            methodName = null
          )
        }
        else 0

        if (exitCode5 == NO_TESTS_ERROR && exitCode34 == NO_TESTS_ERROR &&
            // only check on the first (full) attempt
            attempt == 1 &&
            // a bucket might be empty for run configurations with too few tests due to imperfect tests balancing
            options.bucketsCount < 2) {
          throw NoTestsFound()
        }

        if (attempt == options.attemptCount) break

        if (runJUnit5) {
          val failedClassesJUnit5 = failedClassesJUnit5List.let { if (Files.exists(it)) it.readLines() else emptyList() }
          if (failedClassesJUnit5.isNotEmpty()) {
            context.messages.info("Will rerun JUnit 5 tests: $failedClassesJUnit5")
          }
          else {
            runJUnit5 = false
          }
        }

        if (runJUnit34) {
          val failedClassesJUnit34 = failedClassesJUnit34List.let { if (Files.exists(it)) it.readLines() else emptyList() }
          if (failedClassesJUnit34.isNotEmpty()) {
            context.messages.info("Will rerun JUnit 3+4 tests: $failedClassesJUnit34")
          }
          else {
            runJUnit34 = false
          }
        }
      }
    }
  }

  private class NoTestsFound : Exception()

  private fun runJUnit5Engine(systemProperties: Map<String, String?>,
                              jvmArgs: List<String>,
                              envVariables: Map<String, String>,
                              bootstrapClasspath: List<String>,
                              modulePath: List<String>?,
                              testClasspath: List<String>,
                              suiteName: String?,
                              methodName: String?): Int {
    val args = ArrayList<String>()

    val classpath = ArrayList<String>(bootstrapClasspath)
    if (modulePath == null) {
      appendJUnitStarter(classpath)
    }
    if (!isBootstrapSuiteDefault || isRunningInBatchMode
        || options.isDedicatedTestRuntime != "false"
        || suiteName == null) {
      classpath += testClasspath
    }
    args += "-classpath"
    args += classpath.joinToString(separator = File.pathSeparator)

    if (modulePath != null) {
      args += "--module-path"
      val mp = ArrayList<String>(modulePath)
      appendJUnitStarter(mp)
      args += mp.joinToString(separator = File.pathSeparator)
      args += "--add-modules=ALL-MODULE-PATH"
    }

    args += jvmArgs

    args += "-Dintellij.build.test.runner=junit5"

    for ((k, v) in systemProperties) {
      if (v != null) {
        args += "-D${k}=${v}"
      }
    }

    args += if (suiteName == null) "com.intellij.tests.JUnit5TeamCityRunnerForTestsOnClasspath" else "com.intellij.tests.JUnit5TeamCityRunnerForTestAllSuite"

    if (suiteName != null) {
      args += suiteName
    }

    if (methodName != null) {
      args += methodName
    }

    val argFile = CommandLineWrapperUtil.createArgumentFile(args, Charset.defaultCharset())
    val runtime = runBlocking(Dispatchers.IO) { getRuntimeExecutablePath().toString() }
    context.messages.info("Starting tests on runtime ${runtime}")
    val builder = ProcessBuilder(runtime, "@" + argFile.absolutePath)
    builder.environment().putAll(envVariables)
    builder.inheritIO()
    val exitCode = builder.start().waitFor()
    if (exitCode != 0 && exitCode != NO_TESTS_ERROR) {
      context.messages.error("Tests failed with exit code ${exitCode}")
    }
    return exitCode
  }

  private fun appendJUnitStarter(path: MutableList<String>) {
    for (libName in listOf("JUnit5", "JUnit5Launcher", "JUnit5Vintage", "JUnit5Jupiter")) {
      for (library in context.projectModel.project.libraryCollection.findLibrary(libName)!!.getFiles(JpsOrderRootType.COMPILED)) {
        path.add(library.absolutePath)
      }
    }
  }

  private val isBootstrapSuiteDefault: Boolean
    get() = options.bootstrapSuite == TestingOptions.BOOTSTRAP_SUITE_DEFAULT

  private val isRunningInBatchMode: Boolean
    get() = options.batchTestIncludes != null &&
            options.testPatterns == null &&
            options.testConfigurations == null &&
            options.testGroups == TestingOptions.ALL_EXCLUDE_DEFINED_GROUP

  private fun buildCausalProfilingAgentJvmArg(options: CausalProfilingOptions): List<String> {
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
}

private class MyTraceFileUploader(serverUrl: String, token: String?, private val messages: BuildMessages) : TraceFileUploader(serverUrl,
                                                                                                                              token) {
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

private fun publishTestDiscovery(messages: BuildMessages, file: String?) {
  val serverUrl = System.getProperty("intellij.test.discovery.url")
  val token = System.getProperty("intellij.test.discovery.token")
  messages.info("Trying to upload ${file} into ${serverUrl}.")
  val path = file?.let { Path.of(it) }
  if (path != null && Files.exists(path)) {
    if (serverUrl == null) {
      messages.warning("""
        Test discovery server url is not defined, but test discovery capturing enabled. 
        Will not upload to remote server. Please set 'intellij.test.discovery.url' system property.
        """.trimIndent())
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
      messages.error(e.message!!, e)
    }
  }
  messages.buildStatus("With Discovery, {build.status.text}")
}

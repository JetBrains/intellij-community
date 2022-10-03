// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.TestCaseLoader
import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.lang.UrlClassLoader
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.CompilationTasks.Companion.create
import org.jetbrains.intellij.build.TraceManager.spanBuilder
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
import java.util.function.BiConsumer
import java.util.regex.Pattern
import java.util.stream.Stream

internal class TestingTasksImpl(private val context: CompilationContext, private val options: TestingOptions) : TestingTasks {
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
      val description = e.message?.lineSequence()?.first()?.replace("'", "\"")
      println("##teamcity[buildProblem identity='${name.replace(" ", "_")}' description='$description']")
      emptyList()
    }
  }

  override fun runTests(additionalJvmOptions: List<String>, defaultMainModule: String?, rootExcludeCondition: ((Path) -> Boolean)?) {
    if (options.isTestDiscoveryEnabled && options.isPerformanceTestsOnly) {
      context.messages.buildStatus("Skipping performance testing with Test Discovery, {build.status.text}")
      return
    }

    val mainModule = options.mainModule ?: defaultMainModule!!
    checkOptions(mainModule)

    val compilationTasks = create(context)
    options.beforeRunProjectArtifacts?.splitToSequence(';')?.filterNotTo(HashSet(), String::isEmpty)?.let {
      compilationTasks.buildProjectArtifacts(it)
    }

    val runConfigurations = options.testConfigurations
      ?.splitToSequence(';')
      ?.filter(String::isNotEmpty)
      ?.flatMap(::loadRunConfigurations)
      ?.toList()
    if (runConfigurations != null) {
      compilationTasks.compileModules(listOf("intellij.tools.testsBootstrap"),
                                      listOf("intellij.platform.buildScripts") + runConfigurations.map { it.moduleName })
      compilationTasks.buildProjectArtifacts(runConfigurations.flatMapTo(LinkedHashSet()) { it.requiredArtifacts })
    }
    else {
      compilationTasks.compileModules(listOf("intellij.tools.testsBootstrap"), listOf(mainModule, "intellij.platform.buildScripts"))
    }

    val remoteDebugJvmOptions = System.getProperty("teamcity.remote-debug.jvm.options")
    if (remoteDebugJvmOptions != null) {
      debugTests(remoteDebugJvmOptions, additionalJvmOptions, mainModule, context)
    }
    else {
      val additionalSystemProperties = LinkedHashMap<String, String>()
      val effectiveAdditionalJvmOptions = additionalJvmOptions.toMutableList()
      loadTestDiscovery(effectiveAdditionalJvmOptions, additionalSystemProperties)
      if (runConfigurations == null) {
        runTestsFromGroupsAndPatterns(effectiveAdditionalJvmOptions, mainModule, rootExcludeCondition, additionalSystemProperties, context)
      }
      else {
        runTestsFromRunConfigurations(effectiveAdditionalJvmOptions, runConfigurations, additionalSystemProperties, context)
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
      context.messages.warning("'intellij.build.test.batchTest.includes' option will be ignored as other tests matching options are specified.")
    }
  }

  private fun warnOptionIgnored(specifiedOption: String, ignoredOption: String) =
    context.messages.warning("\'${specifiedOption}\' option is specified, so \'${ignoredOption}\' will be ignored.")

  private fun runTestsFromRunConfigurations(additionalJvmOptions: List<String>,
                                            runConfigurations: List<JUnitRunConfigurationProperties>,
                                            additionalSystemProperties: Map<String, String>,
                                            context: CompilationContext) {
    for (configuration in runConfigurations) {
      spanBuilder("run \'${configuration.name}\' run configuration").useWithScope {
        runTestsFromRunConfiguration(configuration, additionalJvmOptions, additionalSystemProperties, context)
      }
    }
  }

  private fun runTestsFromRunConfiguration(runConfigurationProperties: JUnitRunConfigurationProperties,
                                           additionalJvmOptions: List<String>,
                                           additionalSystemProperties: Map<String, String>,
                                           context: CompilationContext) {
    context.messages.progress("Running \'${runConfigurationProperties.name}\' run configuration")
    runTestsProcess(mainModule = runConfigurationProperties.moduleName,
                    testGroups = null,
                    testPatterns = runConfigurationProperties.testClassPatterns.joinToString(separator = ";"),
                    jvmArgs = removeStandardJvmOptions(runConfigurationProperties.vmParameters) + additionalJvmOptions,
                    systemProperties = additionalSystemProperties,
                    envVariables = runConfigurationProperties.envVariables,
                    remoteDebugging = false,
                    context = context)
  }

  private fun runTestsFromGroupsAndPatterns(additionalJvmOptions: List<String>,
                                            defaultMainModule: String?,
                                            rootExcludeCondition: ((Path) -> Boolean)?,
                                            additionalSystemProperties: MutableMap<String, String>,
                                            context: CompilationContext) {
    if (rootExcludeCondition != null) {
      val excludedRoots = ArrayList<String>()
      for (module in context.project.modules) {
        val contentRoots = module.contentRootsList.urls
        if (!contentRoots.isEmpty() && rootExcludeCondition(Path.of(JpsPathUtil.urlToPath(contentRoots.first())))) {
          sequenceOf(context.getModuleOutputDir(module), Path.of(context.getModuleTestsOutputPath(module))).forEach {
            if (Files.exists(it)) {
              excludedRoots += it.toString()
            }
          }
        }
      }

      val excludedRootsFile = context.paths.tempDir.resolve("excluded.classpath")
      Files.createDirectories(excludedRootsFile.parent)
      Files.writeString(excludedRootsFile, excludedRoots.joinToString(separator = "\n"))
      additionalSystemProperties["exclude.tests.roots.file"] = excludedRootsFile.toString()
    }

    runTestsProcess(mainModule = options.mainModule ?: defaultMainModule!!,
                    testGroups = options.testGroups,
                    testPatterns = options.testPatterns,
                    jvmArgs = additionalJvmOptions,
                    systemProperties = additionalSystemProperties,
                    remoteDebugging = false,
                    context = context)
  }

  private fun loadTestDiscovery(additionalJvmOptions: MutableList<String>, additionalSystemProperties: LinkedHashMap<String, String>) {
    if (!options.isTestDiscoveryEnabled) {
      return
    }

    val testDiscovery = "intellij-test-discovery"
    val library = context.projectModel.project.libraryCollection.findLibrary(testDiscovery)
                  ?: throw RuntimeException("Can\'t find the ${testDiscovery} library, but test discovery capturing enabled.")

    val agentJar = library.getPaths(JpsOrderRootType.COMPILED)
      .firstOrNull {
        val name = it.fileName.toString()
        name.startsWith("intellij-test-discovery") && name.endsWith(".jar")
      } ?: throw RuntimeException("Can\'t find the agent in ${testDiscovery} library, but test discovery capturing enabled.")

    additionalJvmOptions += "-javaagent:${agentJar}"
    val excludeRoots = context.projectModel.global.libraryCollection.getLibraries(JpsJavaSdkType.INSTANCE)
      .mapTo(LinkedHashSet()) { FileUtilRt.toSystemDependentName(it.properties.homePath) }
    excludeRoots += context.paths.buildOutputDir.toString()
    excludeRoots += context.paths.projectHome.resolve("out").toString()

    additionalSystemProperties["test.discovery.listener"] = "com.intellij.TestDiscoveryBasicListener"
    additionalSystemProperties["test.discovery.data.listener"] = "com.intellij.rt.coverage.data.SingleTrFileDiscoveryProtocolDataListener"
    additionalSystemProperties["org.jetbrains.instrumentation.trace.file"] = testDiscoveryTraceFilePath

    options.testDiscoveryIncludePatterns?.let { additionalSystemProperties["test.discovery.include.class.patterns"] = it }
    options.testDiscoveryExcludePatterns?.let { additionalSystemProperties["test.discovery.exclude.class.patterns"] = it }

    additionalSystemProperties["test.discovery.excluded.roots"] = excludeRoots.joinToString(separator = ";")
  }

  private val testDiscoveryTraceFilePath: String
    get() = options.testDiscoveryTraceFilePath ?: context.paths.projectHome.resolve("intellij-tracing/td.tr").toString()

  private fun debugTests(remoteDebugJvmOptions: String,
                         additionalJvmOptions: List<String>,
                         defaultMainModule: String?,
                         context: CompilationContext) {
    val testConfigurationType = System.getProperty("teamcity.remote-debug.type")
    if (testConfigurationType != "junit") {
      context.messages.error("Remote debugging is supported for junit run configurations only, but \'teamcity.remote-debug.type\' is ${testConfigurationType}")
    }
    val testObject = System.getProperty("teamcity.remote-debug.junit.type")
    val junitClass = System.getProperty("teamcity.remote-debug.junit.class")
    if (testObject != "class") {
      val message = "Remote debugging supports debugging all test methods in a class for now, debugging isn\'t supported for \'${testObject}\'"
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
    runTestsProcess(mainModule = options.mainModule ?: defaultMainModule!!,
                    testGroups = null,
                    testPatterns = junitClass,
                    jvmArgs = removeStandardJvmOptions(StringUtilRt.splitHonorQuotes(remoteDebugJvmOptions, ' ')) + additionalJvmOptions,
                    systemProperties = emptyMap(),
                    remoteDebugging = true,
                    context = context)
  }

  private fun runTestsProcess(mainModule: String,
                              testGroups: String?,
                              testPatterns: String?,
                              jvmArgs: List<String>,
                              systemProperties: Map<String, String>,
                              envVariables: Map<String, String> = emptyMap(),
                              remoteDebugging: Boolean,
                              context: CompilationContext) {
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
    val modulePath : List<String>?
    
    val moduleInfoFile = JpsJavaExtensionService.getInstance().getJavaModuleIndex(context.project).getModuleInfoFile(mainJpsModule, true)
    val toStringConverter: (File) -> String? = { if (it.exists()) it.absolutePath else null }
    if (moduleInfoFile != null) {
      val outputDir = ModuleBuildTarget(mainJpsModule, JavaModuleBuildTargetType.TEST).outputDir
      val pair = ModulePathSplitter().splitPath(moduleInfoFile, mutableSetOf(outputDir), HashSet(testRoots))
      modulePath = pair.first.path.mapNotNull(toStringConverter)
      testClasspath = pair.second.mapNotNull(toStringConverter)
    }
    else {
      modulePath = null
      testClasspath = testRoots.mapNotNull(toStringConverter)
    }
    val bootstrapClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule("intellij.tools.testsBootstrap"), false)
    val classpathFile = context.paths.tempDir.resolve("junit.classpath")
    Files.createDirectories(classpathFile.parent)
    // this is required to collect tests both on class and module paths
    Files.writeString(classpathFile, testRoots.mapNotNull(toStringConverter).joinToString(separator = "\n"))
    val allSystemProperties = LinkedHashMap<String, String>(systemProperties)
    allSystemProperties.putIfAbsent("classpath.file", classpathFile.toString())
    testPatterns?.let { allSystemProperties.putIfAbsent("intellij.build.test.patterns", it) }
    testGroups?.let { allSystemProperties.putIfAbsent("intellij.build.test.groups", it) }
    allSystemProperties.putIfAbsent("intellij.build.test.sorter", System.getProperty("intellij.build.test.sorter"))
    allSystemProperties.putIfAbsent("bootstrap.testcases", "com.intellij.AllTests")
    allSystemProperties.putIfAbsent(TestingOptions.PERFORMANCE_TESTS_ONLY_FLAG, options.isPerformanceTestsOnly.toString())
    val allJvmArgs = ArrayList(jvmArgs)
    prepareEnvForTestRun(allJvmArgs, allSystemProperties, bootstrapClasspath.toMutableList(), remoteDebugging)
    val messages = context.messages
    if (isRunningInBatchMode) {
      messages.info("Running tests from ${mainModule} matched by \'${options.batchTestIncludes}\' pattern.")
    }
    else {
      messages.info("Starting tests from groups \'${testGroups}\' from classpath of module \'${mainModule}\'")
    }
    val numberOfBuckets = allSystemProperties[TestCaseLoader.TEST_RUNNERS_COUNT_FLAG]?.toIntOrNull() ?: 1
    if (numberOfBuckets > 1) {
      messages.info("Tests from bucket ${allSystemProperties[TestCaseLoader.TEST_RUNNER_INDEX_FLAG]} of ${numberOfBuckets} will be executed")
    }
    messages.block("Test classpath and runtime info") {
      runBlocking(Dispatchers.IO) {
        val runtime = getRuntimeExecutablePath().toString()
        messages.info("Runtime: ${runtime}")
        runProcess(args = listOf(runtime, "-version"), inheritOut = true)
      }
      messages.info("Runtime options: ${allJvmArgs}")
      messages.info("System properties: ${allSystemProperties}")
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
                    systemProperties = allSystemProperties,
                    jvmArgs = allJvmArgs,
                    envVariables = envVariables,
                    bootstrapClasspath = bootstrapClasspath,
                    modulePath = modulePath,
                    testClasspath = testClasspath,
                    numberOfBuckets = numberOfBuckets)
    notifySnapshotBuilt(allJvmArgs)
  }

  private suspend fun getRuntimeExecutablePath(): Path {
    val runtimeDir: Path
    if (options.customRuntimePath != null) {
      runtimeDir = Path.of(options.customRuntimePath)
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
      context.notifyArtifactWasBuilt(file)
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
                                    remoteDebugging: Boolean) {
    if (jvmArgs.contains("-Djava.system.class.loader=com.intellij.util.lang.UrlClassLoader")) {
      val utilModule = context.findRequiredModule("intellij.platform.util")
      val enumerator = JpsJavaExtensionService.dependencies(utilModule)
        .recursively()
        .withoutSdk()
        .includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
      val utilClasspath = enumerator.classes().roots.mapTo(LinkedHashSet()) { it.absolutePath }
      utilClasspath.removeAll(classPath.toSet())
      classPath += utilClasspath
    }

    val snapshotsDir = createSnapshotsDirectory()
    val hprofSnapshotFilePath = snapshotsDir.resolve("intellij-tests-oom.hprof").toString()
    jvmArgs.addAll(0, listOf("-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=${hprofSnapshotFilePath}"))

    jvmArgs.addAll(0, VmOptionsGenerator.computeVmOptions(true, null))

    jvmArgs += options.jvmMemoryOptions?.split(Regex("\\s+")) ?: listOf("-Xms750m", "-Xmx750m")

    val tempDir = System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir"))
    @Suppress("SpellCheckingInspection")
    mapOf(
      "idea.platform.prefix" to options.platformPrefix,
      "idea.home.path" to context.paths.projectHome.toString(),
      "idea.config.path" to "${tempDir}/config",
      "idea.system.path" to "${tempDir}/system",
      "intellij.build.compiled.classes.archives.metadata" to System.getProperty("intellij.build.compiled.classes.archives.metadata"),
      "intellij.build.compiled.classes.archive" to System.getProperty("intellij.build.compiled.classes.archive"),
      BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY to "${context.classesOutputDirectory}",
      "idea.coverage.enabled.build" to System.getProperty("idea.coverage.enabled.build"),
      "teamcity.buildConfName" to System.getProperty("teamcity.buildConfName"),
      "java.io.tmpdir" to tempDir,
      "teamcity.build.tempDir" to tempDir,
      "teamcity.tests.recentlyFailedTests.file" to System.getProperty("teamcity.tests.recentlyFailedTests.file"),
      "teamcity.build.branch.is_default" to System.getProperty("teamcity.build.branch.is_default"),
      "jna.nosys" to "true",
      "file.encoding" to "UTF-8",
      "io.netty.leakDetectionLevel" to "PARANOID",
      "kotlinx.coroutines.debug" to "on",
      "sun.io.useCanonCaches" to "false",
    ).forEach { (k, v) ->
      if (v != null) {
        systemProperties.putIfAbsent(k, v)
      }
    }

    System.getProperties().forEach(BiConsumer { key, value ->
      if ((key as String).startsWith("pass.")) {
        systemProperties[key.substring("pass.".length)] = value as String
      }
    })

    systemProperties[BuildOptions.USE_COMPILED_CLASSES_PROPERTY] = context.options.useCompiledClassesFromProjectOutput.toString()

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
  }

  override fun runTestsSkippedInHeadlessEnvironment() {
    create(context).compileAllModulesAndTests()
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
    val classloader = UrlClassLoader.build().files(classpath).usePersistentClasspathIndexForLocalClassDirectories(false).get()
    val testAnnotation = classloader.loadClass("com.intellij.testFramework.SkipInHeadlessEnvironment")
    return context.project.modules.parallelStream()
      .flatMap { module ->
        val root = Path.of(context.getModuleTestsOutputPath(module))
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
              .map { Pair(it, module.name) }.toList()
          }.stream()
        }
        else {
          Stream.empty()
        }
      }
      .toList()
  }

  private fun runInBatchMode(mainModule: String,
                             systemProperties: Map<String, String>,
                             jvmArgs: List<String>,
                             envVariables: Map<String, String>,
                             bootstrapClasspath: List<String>,
                             testClasspath: List<String>) {
    val mainModuleTestsOutput = context.getModuleTestsOutputPath(context.findRequiredModule(mainModule))
    val pattern = Pattern.compile(FileUtil.convertAntToRegexp(options.batchTestIncludes!!))
    val root = Path.of(mainModuleTestsOutput)
    val testClasses = Files.walk(root).use { stream ->
      stream.map { FileUtilRt.toSystemIndependentName(root.relativize(it).toString()) }.filter { pattern.matcher(it).matches() }.toList()
    }

    if (testClasses.isEmpty()) {
      throw RuntimeException("No tests were found in ${root} with ${pattern}")
    }

    var noTestsInAllClasses = true
    for (testClass in testClasses) {
      val qName = FileUtilRt.getNameWithoutExtension(testClass).replace('/', '.')
      val files = testClasspath.map { Path.of(it) }
      try {
        var noTests = true
        val loader = UrlClassLoader.build().files(files).get()
        val aClass = loader.loadClass(qName)
        @Suppress("UNCHECKED_CAST")
        val testAnnotation = loader.loadClass("org.junit.Test") as Class<Annotation>
        for (m in aClass.declaredMethods) {
          if (Modifier.isPublic(m.modifiers) && m.isAnnotationPresent(testAnnotation)) {
            val exitCode = runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, null, testClasspath, qName, m.name)
            noTests = noTests && exitCode == NO_TESTS_ERROR
          }
        }
        if (noTests) {
          val exitCode3 = runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, null, testClasspath, qName, null)
          noTests = exitCode3 == NO_TESTS_ERROR
        }
        noTestsInAllClasses = noTestsInAllClasses && java.lang.Boolean.TRUE == noTests
      }
      catch (e: Throwable) {
        throw RuntimeException("Failed to process ${qName}", e)
      }
    }

    if (noTestsInAllClasses) {
      throw RuntimeException("No tests were found in ${mainModule}")
    }
  }

  private fun runJUnit5Engine(mainModule: String,
                              systemProperties: Map<String, String>,
                              jvmArgs: List<String>,
                              envVariables: Map<String, String>,
                              bootstrapClasspath: List<String>,
                              modulePath : List<String>?,
                              testClasspath: List<String>,
                              numberOfBuckets: Int) {
    if (isRunningInBatchMode) {
      spanBuilder("run tests in batch mode")
        .setAttribute(AttributeKey.stringKey("pattern"), options.batchTestIncludes ?: "")
        .useWithScope {
          runInBatchMode(mainModule, systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath)
        }
    }
    else {
      val exitCode5 = spanBuilder("run junit 5 tests").useWithScope {
        runJUnit5Engine(systemProperties = systemProperties,
                        jvmArgs = jvmArgs,
                        envVariables = envVariables,
                        bootstrapClasspath = bootstrapClasspath,
                        modulePath = modulePath,
                        testClasspath = testClasspath,
                        suiteName = null,
                        methodName = null)
      }
      val exitCode3 = spanBuilder("run junit 3 tests").useWithScope {
        runJUnit5Engine(systemProperties = systemProperties,
                        jvmArgs = jvmArgs,
                        envVariables = envVariables,
                        bootstrapClasspath = bootstrapClasspath,
                        modulePath = modulePath,
                        testClasspath = testClasspath,
                        suiteName = options.bootstrapSuite,
                        methodName = null)
      }
      if (exitCode5 == NO_TESTS_ERROR && exitCode3 == NO_TESTS_ERROR &&
          // bucket may be empty for run configurations with too few tests due to imperfect tests balancing
          numberOfBuckets < 2) {
        throw RuntimeException("No tests were found in the configuration")
      }
    }
  }

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
    if (!isBootstrapSuiteDefault || isRunningInBatchMode || suiteName == null) {
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
    val runtime = runBlocking(Dispatchers.IO) { getRuntimeExecutablePath ().toString() }
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

private class MyTraceFileUploader(serverUrl: String, token: String?, private val messages: BuildMessages) : TraceFileUploader(serverUrl, token) {
  override fun log(message: String) = messages.info(message)
}

private val ignoredPrefixes = listOf(
  "-ea", "-XX:+HeapDumpOnOutOfMemoryError", "-Xbootclasspath", "-Xmx", "-Xms",
  "-Didea.system.path=", "-Didea.config.path=", "-Didea.home.path=")

private fun removeStandardJvmOptions(vmOptions: List<String>): List<String> =
  vmOptions.filter { option -> ignoredPrefixes.none(option::startsWith) }

private fun publishTestDiscovery(messages: BuildMessages, file: String?) {
  val serverUrl = System.getProperty("intellij.test.discovery.url")
  val token = System.getProperty("intellij.test.discovery.token")
  messages.info("Trying to upload ${file} into ${serverUrl}.")
  if (file != null && File(file).exists()) {
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
      map["checkout-root-prefix"] = System.getProperty("intellij.build.test.discovery.checkout.root.prefix")
      uploader.upload(Path.of(file), map)
    }
    catch (e: Exception) {
      messages.error(e.message!!, e)
    }
  }
  messages.buildStatus("With Discovery, {build.status.text}")
}

private const val NO_TESTS_ERROR = 42

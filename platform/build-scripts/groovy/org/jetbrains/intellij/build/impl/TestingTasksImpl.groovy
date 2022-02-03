// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.TestCaseLoader
import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.SystemProperties
import com.intellij.util.lang.UrlClassLoader
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.ant.AntClassLoader
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.causal.CausalProfilingOptions
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Test

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.jar.Manifest
import java.util.regex.Pattern
import java.util.stream.Stream

@CompileStatic
class TestingTasksImpl extends TestingTasks {
  protected final CompilationContext context
  protected final TestingOptions options

  TestingTasksImpl(CompilationContext context, TestingOptions options) {
    this.options = options
    this.context = context
  }

  @Override
  void runTests(List<String> additionalJvmOptions, String defaultMainModule, Predicate<File> rootExcludeCondition) {
    if (options.testDiscoveryEnabled && options.performanceTestsOnly) {
      context.messages.buildStatus("Skipping performance testing with Test Discovery, {build.status.text}")
      return
    }

    checkOptions()

    CompilationTasks compilationTasks = CompilationTasks.create(context)
    Set<String> projectArtifacts = options.beforeRunProjectArtifacts == null ? null : Set.of(options.beforeRunProjectArtifacts.split(";"))
    if (projectArtifacts != null) {
      compilationTasks.buildProjectArtifacts(projectArtifacts)
    }
    def runConfigurations = options.testConfigurations?.split(";")?.collect { String name ->
      def file = JUnitRunConfigurationProperties.findRunConfiguration(context.paths.projectHome, name, context.messages)
      JUnitRunConfigurationProperties.loadRunConfiguration(file, context.messages)
    }
    if (runConfigurations != null) {
      compilationTasks.compileModules(["intellij.tools.testsBootstrap"], ["intellij.platform.buildScripts"] + runConfigurations.collect { it.moduleName })
      compilationTasks.buildProjectArtifacts((Set<String>)runConfigurations.collectMany(new LinkedHashSet<String>()) {it.requiredArtifacts})
    }
    else if (options.mainModule != null) {
      compilationTasks.compileModules(["intellij.tools.testsBootstrap"], [options.mainModule, "intellij.platform.buildScripts"])
    }
    else {
      compilationTasks.compileAllModulesAndTests()
    }

    setupTestingDependencies()

    String remoteDebugJvmOptions = System.getProperty("teamcity.remote-debug.jvm.options")
    if (remoteDebugJvmOptions != null) {
      debugTests(remoteDebugJvmOptions, additionalJvmOptions, defaultMainModule, rootExcludeCondition, context)
    }
    else {
      Map<String, String> additionalSystemProperties = new LinkedHashMap<>()
      loadTestDiscovery(additionalJvmOptions, additionalSystemProperties)

      if (runConfigurations != null) {
        runTestsFromRunConfigurations(additionalJvmOptions, runConfigurations, additionalSystemProperties, context)
      }
      else {
        runTestsFromGroupsAndPatterns(additionalJvmOptions, defaultMainModule, rootExcludeCondition, additionalSystemProperties, context)
      }
      if (options.testDiscoveryEnabled) {
        publishTestDiscovery(context.messages, getTestDiscoveryTraceFilePath())
      }
    }
  }

  private void checkOptions() {
    if (options.testConfigurations != null) {
      String testConfigurationsOptionName = "intellij.build.test.configurations"
      if (options.testPatterns != null) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.patterns")
      }
      if (options.testGroups != TestingOptions.ALL_EXCLUDE_DEFINED_GROUP) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.groups")
      }
      if (options.mainModule != null) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.main.module")
      }
    }
    else if (options.testPatterns != null && options.testGroups != TestingOptions.ALL_EXCLUDE_DEFINED_GROUP) {
      warnOptionIgnored("intellij.build.test.patterns", "intellij.build.test.groups")
    }

    if (options.batchTestIncludes != null && !isRunningInBatchMode()) {
      context.messages.warning("'intellij.build.test.batchTest.includes' option will be ignored as other tests matching options are specified.")
    }
  }

  private void warnOptionIgnored(String specifiedOption, String ignoredOption) {
    context.messages.warning("'$specifiedOption' option is specified so '$ignoredOption' will be ignored.")
  }

  private void runTestsFromRunConfigurations(List<String> additionalJvmOptions,
                                             List<JUnitRunConfigurationProperties> runConfigurations,
                                             Map<String, String> additionalSystemProperties,
                                             CompilationContext context) {
    for (configuration in runConfigurations) {
      context.messages.block("Run '${configuration.name}' run configuration", new Supplier<Void>() {
        @Override
        Void get() {
          runTestsFromRunConfiguration(configuration, additionalJvmOptions, additionalSystemProperties, context)
          return null
        }
      })
    }
  }

  private void runTestsFromRunConfiguration(JUnitRunConfigurationProperties runConfigurationProperties,
                                            List<String> additionalJvmOptions,
                                            Map<String, String> additionalSystemProperties,
                                            CompilationContext context) {
    context.messages.progress("Running '${runConfigurationProperties.name}' run configuration")
    List<String> filteredVmOptions = removeStandardJvmOptions(runConfigurationProperties.vmParameters)
    runTestsProcess(runConfigurationProperties.moduleName,
                    null,
                    runConfigurationProperties.testClassPatterns.join(";"),
                    filteredVmOptions + additionalJvmOptions,
                    additionalSystemProperties,
                    runConfigurationProperties.envVariables,
                    false,
                    context)
  }

  private static List<String> removeStandardJvmOptions(List<String> vmOptions) {
    List<String> ignoredPrefixes = [
      "-ea", "-XX:+HeapDumpOnOutOfMemoryError",
      "-Xbootclasspath",
      "-Xmx", "-Xms",
      "-Didea.system.path=", "-Didea.config.path=", "-Didea.home.path="
    ]
    return vmOptions.findAll { option -> ignoredPrefixes.every { !option.startsWith(it) } }
  }

  private void runTestsFromGroupsAndPatterns(List<String> additionalJvmOptions,
                                             String defaultMainModule,
                                             Predicate<File> rootExcludeCondition,
                                             Map<String, String> additionalSystemProperties,
                                             CompilationContext context) {
    String mainModule = options.mainModule ?: defaultMainModule
    if (rootExcludeCondition != null) {
      List<String> excludedRoots = new ArrayList<String>()
      for (JpsModule module : context.project.modules) {
        List<String> contentRoots = module.contentRootsList.getUrls()
        if (!contentRoots.isEmpty() && rootExcludeCondition.test(JpsPathUtil.urlToFile(contentRoots.first()))) {
          Path dir = context.getModuleOutputDir(module)
          if (Files.exists(dir)) {
            excludedRoots.add(dir.toString())
          }
          dir = Path.of(context.getModuleTestsOutputPath(module))
          if (Files.exists(dir)) {
            excludedRoots.add(dir.toString())
          }
        }
      }
      Path excludedRootsFile = context.paths.tempDir.resolve("excluded.classpath")
      Files.createDirectories(excludedRootsFile.parent)
      Files.writeString(excludedRootsFile, String.join("\n", excludedRoots))
      additionalSystemProperties.put("exclude.tests.roots.file", excludedRootsFile.toString())
    }

    runTestsProcess(mainModule, options.testGroups, options.testPatterns, additionalJvmOptions, additionalSystemProperties,
                    Collections.<String, String>emptyMap(), false, context)
  }

  private loadTestDiscovery(List<String> additionalJvmOptions, LinkedHashMap<String, String> additionalSystemProperties) {
    if (options.testDiscoveryEnabled) {
      def testDiscovery = "intellij-test-discovery"
      JpsLibrary library = context.projectModel.project.libraryCollection.findLibrary(testDiscovery)
      if (library == null) context.messages.error("Can't find the $testDiscovery library, but test discovery capturing enabled.")
      def agentJar = library.getFiles(JpsOrderRootType.COMPILED).find { it.name.startsWith("intellij-test-discovery") && it.name.endsWith(".jar") }
      if (agentJar == null) context.messages.error("Can't find the agent in $testDiscovery library, but test discovery capturing enabled.")

      additionalJvmOptions.add("-javaagent:${agentJar.absolutePath}" as String)

      def excludeRoots = new LinkedHashSet<String>()
      context.projectModel.global.getLibraryCollection()
        .getLibraries(JpsJavaSdkType.INSTANCE)
        .each { excludeRoots.add(it.getProperties().getHomePath()) }
      excludeRoots.add(context.paths.buildOutputRoot)
      excludeRoots.add("$context.paths.projectHome/out".toString())

      additionalSystemProperties.putAll(
        [
          "test.discovery.listener"                 : "com.intellij.TestDiscoveryBasicListener",
          "test.discovery.data.listener"            : "com.intellij.rt.coverage.data.SingleTrFileDiscoveryProtocolDataListener",
          "org.jetbrains.instrumentation.trace.file": getTestDiscoveryTraceFilePath(),
          "test.discovery.include.class.patterns"   : options.testDiscoveryIncludePatterns,
          "test.discovery.exclude.class.patterns"   : options.testDiscoveryExcludePatterns,
          // "test.discovery.affected.roots"           : FileUtilRt.toSystemDependentName(context.paths.projectHome),
          "test.discovery.excluded.roots"           : excludeRoots.collect { FileUtilRt.toSystemDependentName(it) }.join(";"),
        ] as Map<String, String>)
    }
  }

  private String getTestDiscoveryTraceFilePath() {
    return options.testDiscoveryTraceFilePath ?: "${context.paths.projectHome}/intellij-tracing/td.tr"
  }

  private static publishTestDiscovery(BuildMessages messages, String file) {
    String serverUrl = System.getProperty("intellij.test.discovery.url")
    String token = System.getProperty("intellij.test.discovery.token")

    messages.info("Trying to upload $file into $serverUrl.")
    if (file != null && new File(file).exists()) {
      if (serverUrl == null) {
        messages.warning("Test discovery server url is not defined, but test discovery capturing enabled. \n" +
                         "Will not upload to remote server. Please set 'intellij.test.discovery.url' system property.")
        return
      }

      TraceFileUploader uploader = new MyTraceFileUploader(serverUrl, token, messages)
      try {
        uploader.upload(new File(file), [
          'teamcity-build-number'            : System.getProperty('build.number'),
          'teamcity-build-type-id'           : System.getProperty('teamcity.buildType.id'),
          'teamcity-build-configuration-name': System.getenv('TEAMCITY_BUILDCONF_NAME'),
          'teamcity-build-project-name'      : System.getenv('TEAMCITY_PROJECT_NAME'),
          'branch'                           : System.getProperty('teamcity.build.branch') ?: 'master',
          'project'                          : System.getProperty('intellij.test.discovery.project') ?: 'intellij',
          'checkout-root-prefix'             : System.getProperty("intellij.build.test.discovery.checkout.root.prefix"),
        ])
      }
      catch (Exception e) {
        messages.error(e.message, e)
      }
    }
    messages.buildStatus("With Discovery, {build.status.text}")
  }

  private static final class MyTraceFileUploader extends TraceFileUploader {
    private final BuildMessages messages

    MyTraceFileUploader(@NotNull String serverUrl, @Nullable String token, BuildMessages messages) {
      super(serverUrl, token)
      this.messages = messages
    }

    @Override
    protected void log(String message) {
      this.messages.info(message)
    }
  }

  private void debugTests(String remoteDebugJvmOptions,
                          List<String> additionalJvmOptions,
                          String defaultMainModule,
                          Predicate<File> rootExcludeCondition,
                          CompilationContext context) {
    def testConfigurationType = System.getProperty("teamcity.remote-debug.type")
    if (testConfigurationType != "junit") {
      context.messages.error("Remote debugging is supported for junit run configurations only, but 'teamcity.remote-debug.type' is $testConfigurationType")
    }

    def testObject = System.getProperty("teamcity.remote-debug.junit.type")
    def junitClass = System.getProperty("teamcity.remote-debug.junit.class")
    if (testObject != "class") {
      def message = "Remote debugging supports debugging all test methods in a class for now, debugging isn't supported for '$testObject'"
      if (testObject == "method") {
        context.messages.warning(message)
        context.messages.warning("Launching all test methods in the class $junitClass")
      } else {
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
    String mainModule = options.mainModule ?: defaultMainModule
    List<String> filteredOptions = removeStandardJvmOptions(StringUtilRt.splitHonorQuotes(remoteDebugJvmOptions, ' ' as char))
    runTestsProcess(mainModule,
                    null,
                    junitClass,
                    filteredOptions + additionalJvmOptions,
                    Collections.<String, String>emptyMap(),
                    Collections.<String, String>emptyMap(),
                    true,
                    context)
  }

  private void runTestsProcess(String mainModule,
                               String testGroups,
                               String testPatterns,
                               List<String> jvmArgs,
                               Map<String, String> systemProperties,
                               Map<String, String> envVariables,
                               boolean remoteDebugging,
                               CompilationContext context) {
    List<String> testsClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule(mainModule), true)
    List<String> bootstrapClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule("intellij.tools.testsBootstrap"), false)

    Path classpathFile = context.paths.tempDir.resolve("junit.classpath")
    Files.createDirectories(classpathFile.parent)

    StringBuilder classPathString = new StringBuilder()
    for (String s : testsClasspath) {
      if (Files.exists(Path.of(s))) {
        classPathString.append(s).append('\n' as char)
      }
    }
    if (classPathString.size() > 0) {
      classPathString.setLength(classPathString.size() - 1)
    }
    Files.writeString(classpathFile, classPathString)

    Map<String, String> allSystemProperties = new HashMap<String, String>(systemProperties)
    allSystemProperties.putIfAbsent("classpath.file", classpathFile.toString())
    allSystemProperties.putIfAbsent("intellij.build.test.patterns", testPatterns)
    allSystemProperties.putIfAbsent("intellij.build.test.groups", testGroups)
    allSystemProperties.putIfAbsent("intellij.build.test.sorter", System.getProperty("intellij.build.test.sorter"))
    allSystemProperties.putIfAbsent("bootstrap.testcases", "com.intellij.AllTests")
    allSystemProperties.putIfAbsent(TestingOptions.PERFORMANCE_TESTS_ONLY_FLAG, options.performanceTestsOnly.toString())

    List<String> allJvmArgs = new ArrayList<String>(jvmArgs)

    prepareEnvForTestRun(allJvmArgs, allSystemProperties, bootstrapClasspath, remoteDebugging)

    if (isRunningInBatchMode()) {
      context.messages.info("Running tests from ${mainModule} matched by '${options.batchTestIncludes}' pattern.")
    }
    else {
      context.messages.info("Starting ${(testGroups == null ? "all tests" : "tests from groups '${testGroups}'")} from classpath of module '$mainModule'")
    }
    String numberOfBuckets = allSystemProperties[TestCaseLoader.TEST_RUNNERS_COUNT_FLAG]
    if (numberOfBuckets != null) {
      context.messages.info("Tests from bucket ${allSystemProperties[TestCaseLoader.TEST_RUNNER_INDEX_FLAG]} of $numberOfBuckets will be executed")
    }
    String runtime = runtimeExecutablePath()
    context.messages.info("Runtime: $runtime")
    BuildHelper.runProcess(context, List.of(runtime, "-version"))
    context.messages.info("Runtime options: $allJvmArgs")
    context.messages.info("System properties: $allSystemProperties")
    context.messages.info("Bootstrap classpath: $bootstrapClasspath")
    context.messages.info("Tests classpath: $testsClasspath")
    if (!envVariables.isEmpty()) {
      context.messages.info("Environment variables: $envVariables")
    }

    if (options.preferAntRunner) {
      runJUnitTask(mainModule, allJvmArgs, allSystemProperties, envVariables,
                   isBootstrapSuiteDefault() && !isRunningInBatchMode() ? bootstrapClasspath : testsClasspath)
    }
    else {
      runJUnit5Engine(mainModule, allSystemProperties, allJvmArgs, envVariables, bootstrapClasspath, testsClasspath)
    }
    notifySnapshotBuilt(allJvmArgs)
  }

  private Path runtimeExecutablePath() {
    String binJava = "bin/java"
    String binJavaExe = "bin/java.exe"
    String contentsHome = "Contents/Home"

    Path runtimeDir
    if (options.customRuntimePath != null) {
      runtimeDir = Path.of(options.customRuntimePath)
      if (!Files.isDirectory(runtimeDir)) {
        throw new IllegalStateException("Custom Jre path from system property '" + TestingOptions.TEST_JRE_PROPERTY + "' is missing: " + runtimeDir)
      }
    }
    else {
      runtimeDir = context.bundledRuntime.getHomeForCurrentOsAndArch()
    }

    if (SystemInfoRt.isWindows) {
      Path path = runtimeDir.resolve(binJavaExe)
      if (!Files.exists(path)) {
        throw new IllegalStateException("java.exe is missing: " + path)
      }
      return path
    }

    if (SystemInfoRt.isMac) {
      if (Files.exists(runtimeDir.resolve(binJava))) {
        return runtimeDir.resolve(binJava)
      }

      if (Files.exists(runtimeDir.resolve(contentsHome).resolve(binJava))) {
        return runtimeDir.resolve(contentsHome).resolve(binJava)
      }

      throw new IllegalStateException("java executable is missing under " + runtimeDir)
    }

    if (!Files.exists(runtimeDir.resolve(binJava))) {
      throw new IllegalStateException("java executable is missing: " + runtimeDir.resolve(binJava))
    }

    return runtimeDir.resolve(binJava)
  }

  private void notifySnapshotBuilt(List<String> jvmArgs) {
    String option = "-XX:HeapDumpPath="
    Path file = Path.of(jvmArgs.find { it.startsWith(option) }.substring(option.length()))
    if (Files.exists(file)) {
      context.notifyArtifactWasBuilt(file)
    }
  }

  @Override
  Path createSnapshotsDirectory() {
    Path snapshotsDir = context.paths.projectHomeDir.resolve("out/snapshots")
    NioFiles.deleteRecursively(snapshotsDir)
    Files.createDirectories(snapshotsDir)
    return snapshotsDir
  }

  @Override
  void prepareEnvForTestRun(List<String> jvmArgs, Map<String, String> systemProperties, List<String> classPath, boolean remoteDebugging) {
    if (jvmArgs.contains("-Djava.system.class.loader=com.intellij.util.lang.UrlClassLoader")) {
      def utilModule = context.findRequiredModule("intellij.platform.util")
      JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(utilModule).recursively().withoutSdk().includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
      def utilClasspath = enumerator.classes().roots.collect { it.absolutePath }
      classPath.addAll(utilClasspath - classPath)
    }

    Path snapshotsDir = createSnapshotsDirectory()
    String hprofSnapshotFilePath = snapshotsDir.resolve("intellij-tests-oom.hprof").toString()
    List<String> defaultJvmArgs = VmOptionsGenerator.COMMON_VM_OPTIONS + [
      '-XX:+HeapDumpOnOutOfMemoryError',
      '-XX:HeapDumpPath=' + hprofSnapshotFilePath,
      '-Dkotlinx.coroutines.debug=on', // re-enable coroutine debugging in tests (its is explicitly disabled in VmOptionsGenerator)
    ]
    jvmArgs.addAll(0, defaultJvmArgs)
    if (options.jvmMemoryOptions != null) {
      jvmArgs.addAll(options.jvmMemoryOptions.split())
    }
    else {
      jvmArgs.addAll([
        "-Xmx750m",
        "-Xms750m",
        "-Dsun.io.useCanonCaches=false"
      ])
    }

    String tempDir = System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir"))
    Map<String, String> defaultSystemProperties = [
      "idea.platform.prefix"                              : options.platformPrefix,
      "idea.home.path"                                    : context.paths.projectHome,
      "idea.config.path"                                  : "$tempDir/config".toString(),
      "idea.system.path"                                  : "$tempDir/system".toString(),
      "intellij.build.compiled.classes.archives.metadata" : System.getProperty("intellij.build.compiled.classes.archives.metadata"),
      "intellij.build.compiled.classes.archive"           : System.getProperty("intellij.build.compiled.classes.archive"),
      (BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY): "$context.projectOutputDirectory".toString(),
      "idea.coverage.enabled.build"                       : System.getProperty("idea.coverage.enabled.build"),
      "teamcity.buildConfName"                            : System.getProperty("teamcity.buildConfName"),
      "java.io.tmpdir"                                    : tempDir,
      "teamcity.build.tempDir"                            : tempDir,
      "teamcity.tests.recentlyFailedTests.file"           : System.getProperty("teamcity.tests.recentlyFailedTests.file"),
      "teamcity.build.branch.is_default"                  : System.getProperty("teamcity.build.branch.is_default"),
      "jna.nosys"                                         : "true",
      "file.encoding"                                     : "UTF-8",
      "io.netty.leakDetectionLevel"                       : "PARANOID",
    ] as Map<String, String>
    defaultSystemProperties.forEach(new BiConsumer<String, String>() {
      @Override
      void accept(String k, String v) {
        systemProperties.putIfAbsent(k, v)
      }
    })

    (System.getProperties() as Hashtable<String, String>).each { String key, String value ->
      if (key.startsWith("pass.")) {
        systemProperties[key.substring("pass.".length())] = value
      }
    }

    if (PortableCompilationCache.CAN_BE_USED) {
      systemProperties[BuildOptions.USE_COMPILED_CLASSES_PROPERTY] = "true"
    }

    boolean suspendDebugProcess = options.suspendDebugProcess
    if (options.performanceTestsOnly) {
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
    else if (options.debugEnabled) {
      String debuggerParameter = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspendDebugProcess ? "y" : "n"},address=$options.debugHost:$options.debugPort"
      jvmArgs.add(debuggerParameter)
    }

    if (options.enableCausalProfiling) {
      def causalProfilingOptions = CausalProfilingOptions.IMPL
      systemProperties["intellij.build.test.patterns"] = causalProfilingOptions.testClass.replace(".", "\\.")
      jvmArgs.addAll(buildCausalProfilingAgentJvmArg(causalProfilingOptions))
    }

    if (context.options.bundledRuntimeVersion >= 17) {
      jvmArgs.addAll(OpenedPackages.getCommandLineArguments(context))
    }

    if (suspendDebugProcess) {
      context.messages.info("""
------------->------------- The process suspended until remote debugger connects to debug port -------------<-------------
---------------------------------------^------^------^------^------^------^------^----------------------------------------
""")
    }
  }

  private void runJUnit5Engine(String mainModule,
                               Map<String, String> systemProperties,
                               List<String> jvmArgs,
                               Map<String, String> envVariables,
                               List<String> bootstrapClasspath,
                               List<String> testClasspath) {
    if (isRunningInBatchMode()) {
      String mainModuleTestsOutput = context.getModuleTestsOutputPath(context.findModule(mainModule))
      Pattern pattern = Pattern.compile(FileUtil.convertAntToRegexp(options.batchTestIncludes))
      Path root = Path.of(mainModuleTestsOutput)

      Stream<Path> stream = Files.walk(root)
      try {
        stream
          .filter(new Predicate<Path>() {
            @Override
            boolean test(Path path) {
              return pattern.matcher(root.relativize(path).toString()).matches()
            }
          })
          .forEach(new Consumer<Path>() {
            @Override
            void accept(Path path) {
              String qName = FileUtilRt.getNameWithoutExtension(root.relativize(path).toString()).replaceAll("/", ".")
              List<Path> files = new ArrayList<Path>(testClasspath.size())
              for (String p : testClasspath) {
                files.add(Path.of(p))
              }

              try {
                def noTests = true 
                UrlClassLoader loader = UrlClassLoader.build().files(files).get()
                Class<?> aClazz = Class.forName(qName, false, loader)
                Class<?> testAnnotation = Class.forName(Test.class.getName(), false, loader)
                for (Method m : aClazz.getDeclaredMethods()) {
                  if (m.isAnnotationPresent(testAnnotation as Class<? extends Annotation>) && Modifier.isPublic(m.getModifiers())) {
                    def exitCode =
                      runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath, qName, m.getName())
                    noTests &= exitCode == NO_TESTS_ERROR
                  }
                }
                
                if (noTests) {
                   context.messages.error("No tests were found in the configuration")
                }
              }
              catch (Throwable e) {
                context.messages.error("Failed to process $qName", e)
              }
            }
          })
      }
      finally {
        stream.close()
      }
    }
    else {
      context.messages.info("Run junit 5 tests")
      def exitCode5 = runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath, null, null)
      context.messages.info("Finish junit 5 task")

      context.messages.info("Run junit 3 tests")
      def exitCode3 =
        runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath, options.bootstrapSuite, null)
      context.messages.info("Finish junit 3 task")
      
      if (exitCode5 == NO_TESTS_ERROR && exitCode3 == NO_TESTS_ERROR) {
        context.messages.error("No tests were found in the configuration")
      }
    }
  }
  
  private static final int NO_TESTS_ERROR = 42
  private int runJUnit5Engine(Map<String, String> systemProperties,
                               List<String> jvmArgs,
                               Map<String, String> envVariables,
                               List<String> bootstrapClasspath,
                               List<String> testClasspath, 
                               String suiteName,
                               String methodName) {
    List<String> args = new ArrayList<String>()
    args.add("-classpath")
    List<String> classpath = new ArrayList<String>(bootstrapClasspath)

    for (libName in List.of("JUnit5", "JUnit5Launcher", "JUnit5Vintage", "JUnit5Jupiter")) {
      for (library in context.projectModel.project.libraryCollection.findLibrary(libName).getFiles(JpsOrderRootType.COMPILED)) {
        classpath.add(library.getAbsolutePath())
      }
    }

    if (!isBootstrapSuiteDefault() || isRunningInBatchMode() || suiteName == null) {
      classpath.addAll(testClasspath)
    }
    args.add(classpath.join(File.pathSeparator))
    args.addAll(jvmArgs)

    //noinspection SpellCheckingInspection
    args.add("-Dintellij.build.test.runner=junit5")

    systemProperties.forEach(new BiConsumer<String, String>() {
      @Override
      void accept(String k, String v) {
        if (v != null) {
          args.add("-D" + k + "=" + v)
        }
      }
    })

    String runner = suiteName == null ? "com.intellij.tests.JUnit5AllRunner" : "com.intellij.tests.JUnit5Runner"
    args.add(runner)
    if (suiteName != null) {
      args.add(suiteName)
    }
    if (methodName != null) {
      args.add(methodName)
    }
    File argFile = CommandLineWrapperUtil.createArgumentFile(args, Charset.defaultCharset())
    String runtime = runtimeExecutablePath()
    context.messages.info("Starting tests on runtime " + runtime)
    def builder = new ProcessBuilder(runtime, '@' + argFile.getAbsolutePath())
    builder.environment().putAll(envVariables)
    final Process exec = builder.start()

    def errorReader = new Thread(createInputReader(exec.getErrorStream(), System.err), "Read forked error output")
    errorReader.start()

    def outputReader = new Thread(createInputReader(exec.getInputStream(), System.out), "Read forked output")
    outputReader.start()

    def exitCode = exec.waitFor()
    
    errorReader.join(360_000)
    outputReader.join(360_000)
    if (exitCode != 0 && exitCode != NO_TESTS_ERROR) {
      context.messages.error("Tests failed with exit code $exitCode")
    }
     return exitCode
  }

  private Runnable createInputReader(final InputStream inputStream, final PrintStream outputStream) {
    return new Runnable() {
      @Override
      void run() {
        try {
          final BufferedReader inputReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))
          try {
            while (true) {
              String line = inputReader.readLine()
              if (line == null) break
              outputStream.println(line)
            }
          }
          finally {
            inputReader.close()
          }
        }
        catch (UnsupportedEncodingException ignored) { }
        catch (IOException e) {
          context.messages.error(e.getMessage(), e)
        }
      }
    }
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  private void runJUnitTask(String mainModule,
                            List<String> jvmArgs,
                            Map<String, String> systemProperties,
                            Map<String, String> envVariables,
                            List<String> bootstrapClasspath) {
    defineJunitTask(context.ant, "$context.paths.communityHome/lib")

    String junitTemp = "$context.paths.temp/junit"
    context.ant.mkdir(dir: junitTemp)

    List<String> teamCityFormatterClasspath = createTeamCityFormatterClasspath()

    context.ant.junit(fork: true, showoutput: isShowAntJunitOutput(), logfailedtests: false,
                      tempdir: junitTemp, jvm: runtimeExecutablePath(),
                      printsummary: (underTeamCity ? "off" : "on"),
                      haltOnFailure: (options.failFast ? "yes" : "no")) {
      jvmArgs.each { jvmarg(value: it) }
      systemProperties.each { key, value ->
        if (value != null) {
          sysproperty(key: key, value: value)
        }
      }
      envVariables.each {
        env(key: it.key, value: it.value)
      }

      if (teamCityFormatterClasspath != null) {
        classpath {
          teamCityFormatterClasspath.each {
            pathelement(location: it)
          }
        }
        formatter(classname: "jetbrains.buildServer.ant.junit.AntJUnitFormatter3", usefile: false)
        context.messages.info("Added TeamCity's formatter to JUnit task")
      }
      if (!underTeamCity) {
        classpath {
          pathelement(location: context.getModuleTestsOutputPath(context.findRequiredModule("intellij.platform.buildScripts")))
        }
        formatter(classname: "org.jetbrains.intellij.build.JUnitLiveTestProgressFormatter", usefile: false)
      }

      //test classpath may exceed the maximum command line, so we need to wrap a classpath in a jar
      if (!isBootstrapSuiteDefault()) {
        def classpathJarFile = CommandLineWrapperUtil.createClasspathJarFile(new Manifest(), bootstrapClasspath)
        classpath {
          pathelement(location: classpathJarFile.path)
        }
      } else {
        classpath {
          bootstrapClasspath.each {
            pathelement(location: it)
          }
        }
      }

      if (isRunningInBatchMode()) {
        def mainModuleTestsOutput = context.getModuleTestsOutputPath(context.findModule(mainModule))
        batchtest {
          fileset dir: mainModuleTestsOutput, includes: options.batchTestIncludes
        }
      } else {
        test(name: options.bootstrapSuite)
      }
    }
  }

  /**
   * Allows to disable duplicated lines in TeamCity build log (IDEA-240814).
   *
   * Note! Build statistics (and other TeamCity Service Message) can be reported only with this option enabled (IDEA-241221).
   */
  private static boolean isShowAntJunitOutput() {
    return SystemProperties.getBooleanProperty("intellij.test.show.ant.junit.output", true)
  }

  /**
   * In simple cases when JUnit tests are started from Ant scripts TeamCity will automatically add its formatter to JUnit task. However it
   * doesn't work if JUnit task is called from Groovy code via a new instance of AntBuilder, so in such cases we need to add the formatter
   * explicitly.
   * @return classpath for TeamCity's JUnit formatter or {@code null} if the formatter shouldn't be added
   */
  private List<String> createTeamCityFormatterClasspath() {
    if (!underTeamCity) return null

    if (context.ant.project.buildListeners.any { it.class.name.startsWith("jetbrains.buildServer.") }) {
      context.messages.info("TeamCity's BuildListener is registered in the Ant project so its formatter will be added to JUnit task automatically.")
      return null
    }

    String agentHomeDir = System.getProperty("agent.home.dir")
    if (agentHomeDir == null) {
      context.messages.error("'agent.home.dir' system property isn't set, cannot add TeamCity JARs to classpath.")
    }
    List<String> classpath = [
      "$agentHomeDir/lib/runtime-util.jar",
      "$agentHomeDir/lib/serviceMessages.jar",
      "$agentHomeDir/plugins/antPlugin/ant-runtime.jar",
      "$agentHomeDir/plugins/junitPlugin/junit-runtime.jar",
      "$agentHomeDir/plugins/junitPlugin/junit-support.jar"
    ].collect {it.toString()}
    classpath.each {
      if (!new File(it).exists()) {
        context.messages.error("Cannot add required JARs from $agentHomeDir to classpath: $it doesn't exist")
      }
    }
    return classpath
  }

  protected static boolean isUnderTeamCity() {
    System.getenv("TEAMCITY_VERSION") != null
  }

  static boolean dependenciesInstalled
  void setupTestingDependencies() {
    if (!dependenciesInstalled) {
      dependenciesInstalled = true
      BundledMavenDownloader.downloadMavenCommonLibs(context.paths.buildDependenciesCommunityRoot)
      BundledMavenDownloader.downloadMavenDistribution(context.paths.buildDependenciesCommunityRoot)
    }
  }

  static boolean taskDefined

  /**
   * JUnit is an optional dependency in Ant, so by defining its tasks dynamically we simplify setup for gant/Ant scripts, there is no need
   * to explicitly add its JARs to Ant libraries.
   */
  @CompileDynamic
  static private def defineJunitTask(AntBuilder ant, String communityLib) {
    if (taskDefined) return
    taskDefined = true
    def junitTaskLoaderRef = "JUNIT_TASK_CLASS_LOADER"
    org.apache.tools.ant.types.Path pathJUnit = new org.apache.tools.ant.types.Path(ant.project)
    pathJUnit.createPathElement().setLocation(new File("$communityLib/ant/lib/ant-junit.jar"))
    pathJUnit.createPathElement().setLocation(new File("$communityLib/ant/lib/ant-junit4.jar"))
    ant.project.addReference(junitTaskLoaderRef, new AntClassLoader(ant.project.getClass().getClassLoader(), ant.project, pathJUnit))
    ant.taskdef(name: "junit", classname: "org.apache.tools.ant.taskdefs.optional.junit.JUnitTask", loaderRef: junitTaskLoaderRef)
  }

  protected boolean isBootstrapSuiteDefault() {
    return options.bootstrapSuite == TestingOptions.BOOTSTRAP_SUITE_DEFAULT
  }

  protected boolean isRunningInBatchMode() {
    return options.batchTestIncludes != null &&
           options.testPatterns == null &&
           options.testConfigurations == null &&
           options.testGroups == TestingOptions.ALL_EXCLUDE_DEFINED_GROUP
  }

  private List<String> buildCausalProfilingAgentJvmArg(CausalProfilingOptions options) {
    List<String> causalProfilingJvmArgs = []

    String causalProfilerAgentName = SystemInfoRt.isLinux || SystemInfoRt.isMac ? "liblagent.so" : null
    if (causalProfilerAgentName != null) {
      def agentArgs = options.buildAgentArgsString()
      if (agentArgs != null) {
        causalProfilingJvmArgs << "-agentpath:${System.getProperty("teamcity.build.checkoutDir")}/$causalProfilerAgentName=$agentArgs".toString()
      }
      else {
        context.messages.info("Could not find agent options")
      }
    }
    else {
      context.messages.info("Causal profiling is supported for Linux and Mac only")
    }

    return causalProfilingJvmArgs
  }
}

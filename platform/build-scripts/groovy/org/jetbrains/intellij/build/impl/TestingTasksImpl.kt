// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.TestCaseLoader;
import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.lang.UrlClassLoader;
import groovy.lang.Closure;
import groovy.lang.Reference;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.codehaus.groovy.vmplugin.v5.PluginDefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.*;
import org.jetbrains.intellij.build.causal.CausalProfilingOptions;
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache;
import org.jetbrains.intellij.build.io.ProcessKt;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TestingTasksImpl implements TestingTasks {
  public TestingTasksImpl(CompilationContext context, TestingOptions options) {
    this.options = options;
    this.context = context;
  }

  @Override
  public void runTests(@NotNull List<String> additionalJvmOptions, String defaultMainModule, Predicate<File> rootExcludeCondition) {
    if (options.getTestDiscoveryEnabled() && options.getPerformanceTestsOnly()) {
      context.getMessages().buildStatus("Skipping performance testing with Test Discovery, {build.status.text}");
      return;
    }


    checkOptions();

    CompilationTasks compilationTasks = CompilationTasks.create(context);
    Set<String> projectArtifacts =
      options.getBeforeRunProjectArtifacts() == null ? null : Set.of(options.getBeforeRunProjectArtifacts().split(";"));
    if (projectArtifacts != null) {
      compilationTasks.buildProjectArtifacts(projectArtifacts);
    }

    List<JUnitRunConfigurationProperties> runConfigurations =
      DefaultGroovyMethods.collect(options.getTestConfigurations().split(";"), new Closure<JUnitRunConfigurationProperties>(this, this) {
        public JUnitRunConfigurationProperties doCall(String name) {
          File file =
            RunConfigurationProperties.findRunConfiguration(context.getPaths().getProjectHome(), name, context.getMessages());
          return JUnitRunConfigurationProperties.loadRunConfiguration(file, context.getMessages());
        }
      });
    if (runConfigurations != null) {
      compilationTasks.compileModules(List.of("intellij.tools.testsBootstrap"),
                                      DefaultGroovyMethods.plus(List.of("intellij.platform.buildScripts"),
                                                                DefaultGroovyMethods.collect(runConfigurations,
                                                                                             new Closure<String>(this, this) {
                                                                                               public String doCall(
                                                                                                 JUnitRunConfigurationProperties it) { return it.getModuleName(); }

                                                                                               public String doCall() {
                                                                                                 return doCall(null);
                                                                                               }
                                                                                             })));
      compilationTasks.buildProjectArtifacts((Set<String>)DefaultGroovyMethods.collectMany(runConfigurations, new LinkedHashSet<String>(),
                                                                                           new Closure<List<String>>(this, this) {
                                                                                             public List<String> doCall(
                                                                                               JUnitRunConfigurationProperties it) { return it.getRequiredArtifacts(); }

                                                                                             public List<String> doCall() {
                                                                                               return doCall(null);
                                                                                             }
                                                                                           }));
    }
    else if (options.getMainModule() != null) {
      compilationTasks.compileModules(new ArrayList<String>(Arrays.asList("intellij.tools.testsBootstrap")),
                                      new ArrayList<String>(Arrays.asList(options.getMainModule(), "intellij.platform.buildScripts")));
    }
    else {
      compilationTasks.compileAllModulesAndTests();
    }


    String remoteDebugJvmOptions = System.getProperty("teamcity.remote-debug.jvm.options");
    if (remoteDebugJvmOptions != null) {
      debugTests(remoteDebugJvmOptions, additionalJvmOptions, defaultMainModule, rootExcludeCondition, context);
    }
    else {
      Map<String, String> additionalSystemProperties = new LinkedHashMap<String, String>();
      loadTestDiscovery(additionalJvmOptions, (LinkedHashMap<String, String>)additionalSystemProperties);

      if (runConfigurations != null) {
        runTestsFromRunConfigurations(additionalJvmOptions, runConfigurations, additionalSystemProperties, context);
      }
      else {
        runTestsFromGroupsAndPatterns(additionalJvmOptions, defaultMainModule, rootExcludeCondition, additionalSystemProperties, context);
      }

      if (options.getTestDiscoveryEnabled()) {
        publishTestDiscovery(context.getMessages(), getTestDiscoveryTraceFilePath());
      }
    }
  }

  private void checkOptions() {
    if (options.getTestConfigurations() != null) {
      String testConfigurationsOptionName = "intellij.build.test.configurations";
      if (options.getTestPatterns() != null) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.patterns");
      }

      if (!options.getTestGroups().equals(TestingOptions.ALL_EXCLUDE_DEFINED_GROUP)) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.groups");
      }

      if (options.getMainModule() != null) {
        warnOptionIgnored(testConfigurationsOptionName, "intellij.build.test.main.module");
      }
    }
    else if (options.getTestPatterns() != null && !options.getTestGroups().equals(TestingOptions.ALL_EXCLUDE_DEFINED_GROUP)) {
      warnOptionIgnored("intellij.build.test.patterns", "intellij.build.test.groups");
    }


    if (options.getBatchTestIncludes() != null && !isRunningInBatchMode()) {
      context.getMessages()
        .warning("'intellij.build.test.batchTest.includes' option will be ignored as other tests matching options are specified.");
    }
  }

  private void warnOptionIgnored(String specifiedOption, String ignoredOption) {
    context.getMessages().warning("\'" + specifiedOption + "\' option is specified so \'" + ignoredOption + "\' will be ignored.");
  }

  private void runTestsFromRunConfigurations(final List<String> additionalJvmOptions,
                                             List<JUnitRunConfigurationProperties> runConfigurations,
                                             final Map<String, String> additionalSystemProperties,
                                             final CompilationContext context) {
    for (JUnitRunConfigurationProperties configuration : runConfigurations) {
      context.getMessages().block("Run \'" + configuration.getName() + "\' run configuration", new Supplier<Void>() {
        @Override
        public Void get() {
          runTestsFromRunConfiguration(configuration, additionalJvmOptions, additionalSystemProperties, context);
          return null;
        }
      });
    }
  }

  private void runTestsFromRunConfiguration(final JUnitRunConfigurationProperties runConfigurationProperties,
                                            List<String> additionalJvmOptions,
                                            Map<String, String> additionalSystemProperties,
                                            CompilationContext context) {
    context.getMessages().progress("Running \'" + runConfigurationProperties.getName() + "\' run configuration");
    List<String> filteredVmOptions = removeStandardJvmOptions(runConfigurationProperties.getVmParameters());
    runTestsProcess(runConfigurationProperties.getModuleName(), null,
                    DefaultGroovyMethods.join(runConfigurationProperties.getTestClassPatterns(), ";"),
                    DefaultGroovyMethods.plus(filteredVmOptions, additionalJvmOptions), additionalSystemProperties,
                    runConfigurationProperties.getEnvVariables(), false, context);
  }

  private static List<String> removeStandardJvmOptions(List<String> vmOptions) {
    final List<String> ignoredPrefixes = new ArrayList<String>(
      Arrays.asList("-ea", "-XX:+HeapDumpOnOutOfMemoryError", "-Xbootclasspath", "-Xmx", "-Xms", "-Didea.system.path=",
                    "-Didea.config.path=", "-Didea.home.path="));
    return DefaultGroovyMethods.findAll(vmOptions, new Closure<Boolean>(null, null) {
      public Boolean doCall(final Object option) {
        return DefaultGroovyMethods.every(ignoredPrefixes, new Closure(null, null) {
          public Object doCall(String it) { return !((String)option).startsWith(it); }

          public Object doCall() {
            return doCall(null);
          }
        });
      }
    });
  }

  private void runTestsFromGroupsAndPatterns(List<String> additionalJvmOptions,
                                             String defaultMainModule,
                                             Predicate<File> rootExcludeCondition,
                                             Map<String, String> additionalSystemProperties,
                                             CompilationContext context) {
    final String module1 = options.getMainModule();
    String mainModule = StringGroovyMethods.asBoolean(module1) ? module1 : defaultMainModule;
    if (rootExcludeCondition != null) {
      List<String> excludedRoots = new ArrayList<String>();
      for (JpsModule module : context.getProject().getModules()) {
        List<String> contentRoots = module.getContentRootsList().getUrls();
        if (!contentRoots.isEmpty() && rootExcludeCondition.test(JpsPathUtil.urlToFile(DefaultGroovyMethods.first(contentRoots)))) {
          Path dir = context.getModuleOutputDir(module);
          if (Files.exists(dir)) {
            excludedRoots.add(dir.toString());
          }

          dir = Path.of(context.getModuleTestsOutputPath(module));
          if (Files.exists(dir)) {
            excludedRoots.add(dir.toString());
          }
        }
      }

      Path excludedRootsFile = context.getPaths().getTempDir().resolve("excluded.classpath");
      Files.createDirectories(excludedRootsFile.getParent());
      Files.writeString(excludedRootsFile, String.join("\n", excludedRoots));
      additionalSystemProperties.put("exclude.tests.roots.file", excludedRootsFile.toString());
    }


    runTestsProcess(mainModule, options.getTestGroups(), options.getTestPatterns(), additionalJvmOptions, additionalSystemProperties,
                    Collections.emptyMap(), false, context);
  }

  private void loadTestDiscovery(List<String> additionalJvmOptions, LinkedHashMap<String, String> additionalSystemProperties) {
    if (options.getTestDiscoveryEnabled()) {
      String testDiscovery = "intellij-test-discovery";
      JpsLibrary library = context.getProjectModel().getProject().getLibraryCollection().findLibrary(testDiscovery);
      if (library == null) {
        context.getMessages().error("Can\'t find the " + testDiscovery + " library, but test discovery capturing enabled.");
      }
      final File agentJar = DefaultGroovyMethods.find(library.getFiles(JpsOrderRootType.COMPILED), new Closure<Boolean>(this, this) {
        public Boolean doCall(File it) { return it.getName().startsWith("intellij-test-discovery") && it.getName().endsWith(".jar"); }

        public Boolean doCall() {
          return doCall(null);
        }
      });
      if (agentJar == null) {
        context.getMessages().error("Can\'t find the agent in " + testDiscovery + " library, but test discovery capturing enabled.");
      }

      additionalJvmOptions.add(StringGroovyMethods.asType("-javaagent:" + agentJar.getAbsolutePath(), String.class));

      final LinkedHashSet<String> excludeRoots = new LinkedHashSet<String>();
      DefaultGroovyMethods.each(context.getProjectModel().getGlobal().getLibraryCollection().getLibraries(JpsJavaSdkType.INSTANCE),
                                new Closure<Boolean>(this, this) {
                                  public Boolean doCall(JpsTypedLibrary<JpsSdk<JpsDummyElement>> it) {
                                    return excludeRoots.add(it.getProperties().getHomePath());
                                  }

                                  public Boolean doCall() {
                                    return doCall(null);
                                  }
                                });
      excludeRoots.add(context.getPaths().getBuildOutputRoot());
      excludeRoots.add(context.getPaths().getProjectHome() + "/out".toString());

      additionalSystemProperties.putAll(Map.of(
        "test.discovery.listener", "com.intellij.TestDiscoveryBasicListener",
        "test.discovery.data.listener", "com.intellij.rt.coverage.data.SingleTrFileDiscoveryProtocolDataListener",
        "org.jetbrains.instrumentation.trace.file", getTestDiscoveryTraceFilePath(),
        "test.discovery.include.class.patterns", options.getTestDiscoveryIncludePatterns(),
        "test.discovery.exclude.class.patterns", options.getTestDiscoveryExcludePatterns(),
        // "test.discovery.affected.roots"           : FileUtilRt.toSystemDependentName(context.paths.projectHome),
        "test.discovery.excluded.roots", excludeRoots.stream().map(it -> FileUtilRt.toSystemDependentName(it)).collect(
          Collectors.joining(";"))
      ));
    }
  }

  private String getTestDiscoveryTraceFilePath() {
    final String path = options.getTestDiscoveryTraceFilePath();
    return StringGroovyMethods.asBoolean(path) ? path : context.getPaths().getProjectHome() + "/intellij-tracing/td.tr";
  }

  private static void publishTestDiscovery(BuildMessages messages, String file) {
    String serverUrl = System.getProperty("intellij.test.discovery.url");
    String token = System.getProperty("intellij.test.discovery.token");

    messages.info("Trying to upload " + file + " into " + serverUrl + ".");
    if (file != null && new File(file).exists()) {
      if (serverUrl == null) {
        messages.warning("Test discovery server url is not defined, but test discovery capturing enabled. \n" +
                         "Will not upload to remote server. Please set 'intellij.test.discovery.url' system property.");
        return;
      }


      TraceFileUploader uploader = new MyTraceFileUploader(serverUrl, token, messages);
      try {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(7);
        map.put("teamcity-build-number", System.getProperty("build.number"));
        map.put("teamcity-build-type-id", System.getProperty("teamcity.buildType.id"));
        map.put("teamcity-build-configuration-name", System.getenv("TEAMCITY_BUILDCONF_NAME"));
        map.put("teamcity-build-project-name", System.getenv("TEAMCITY_PROJECT_NAME"));
        final String property = System.getProperty("teamcity.build.branch");
        map.put("branch", StringGroovyMethods.asBoolean(property) ? property : "master");
        final String property1 = System.getProperty("intellij.test.discovery.project");
        map.put("project", StringGroovyMethods.asBoolean(property1) ? property1 : "intellij");
        map.put("checkout-root-prefix", System.getProperty("intellij.build.test.discovery.checkout.root.prefix"));
        uploader.upload(new File(file), map);
      }
      catch (Exception e) {
        messages.error(e.getMessage(), e);
      }
    }

    messages.buildStatus("With Discovery, {build.status.text}");
  }

  private void debugTests(String remoteDebugJvmOptions,
                          List<String> additionalJvmOptions,
                          String defaultMainModule,
                          Predicate<File> rootExcludeCondition,
                          CompilationContext context) {
    String testConfigurationType = System.getProperty("teamcity.remote-debug.type");
    if (!testConfigurationType.equals("junit")) {
      context.getMessages().error(
        "Remote debugging is supported for junit run configurations only, but \'teamcity.remote-debug.type\' is " + testConfigurationType);
    }


    String testObject = System.getProperty("teamcity.remote-debug.junit.type");
    String junitClass = System.getProperty("teamcity.remote-debug.junit.class");
    if (!testObject.equals("class")) {
      String message =
        "Remote debugging supports debugging all test methods in a class for now, debugging isn\'t supported for \'" + testObject + "\'";
      if (testObject.equals("method")) {
        context.getMessages().warning(message);
        context.getMessages().warning("Launching all test methods in the class " + junitClass);
      }
      else {
        context.getMessages().error(message);
      }
    }

    if (junitClass == null) {
      context.getMessages()
        .error("Remote debugging supports debugging all test methods in a class for now, but target class isn't specified");
    }

    if (options.getTestPatterns() != null) {
      context.getMessages().warning("'intellij.build.test.patterns' option is ignored while debugging via TeamCity plugin");
    }

    if (options.getTestConfigurations() != null) {
      context.getMessages().warning("'intellij.build.test.configurations' option is ignored while debugging via TeamCity plugin");
    }

    final String module = options.getMainModule();
    String mainModule = StringGroovyMethods.asBoolean(module) ? module : defaultMainModule;
    List<String> filteredOptions = removeStandardJvmOptions(
      StringUtilRt.splitHonorQuotes(remoteDebugJvmOptions, ' '));
    runTestsProcess(mainModule, null, junitClass, DefaultGroovyMethods.plus(filteredOptions, additionalJvmOptions), Collections.emptyMap(),
                    Collections.emptyMap(), true, context);
  }

  private void runTestsProcess(final String mainModule,
                               final String testGroups,
                               String testPatterns,
                               List<String> jvmArgs,
                               Map<String, String> systemProperties,
                               Map<String, String> envVariables,
                               boolean remoteDebugging,
                               CompilationContext context) {
    List<String> testsClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule(mainModule), true);
    List<String> bootstrapClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule("intellij.tools.testsBootstrap"), false);

    Path classpathFile = context.getPaths().getTempDir().resolve("junit.classpath");
    Files.createDirectories(classpathFile.getParent());

    StringBuilder classPathString = new StringBuilder();
    for (String s : testsClasspath) {
      if (Files.exists(Path.of(s))) {
        classPathString.append(s).append('\n');
      }
    }

    if (PluginDefaultGroovyMethods.size(classPathString) > 0) {
      classPathString.setLength(PluginDefaultGroovyMethods.size(classPathString) - 1);
    }

    Files.writeString(classpathFile, classPathString);

    final Map<String, String> allSystemProperties = new HashMap<String, String>(systemProperties);
    allSystemProperties.putIfAbsent("classpath.file", classpathFile.toString());
    allSystemProperties.putIfAbsent("intellij.build.test.patterns", testPatterns);
    allSystemProperties.putIfAbsent("intellij.build.test.groups", testGroups);
    allSystemProperties.putIfAbsent("intellij.build.test.sorter", System.getProperty("intellij.build.test.sorter"));
    allSystemProperties.putIfAbsent("bootstrap.testcases", "com.intellij.AllTests");
    allSystemProperties.putIfAbsent(TestingOptions.PERFORMANCE_TESTS_ONLY_FLAG, String.valueOf(options.getPerformanceTestsOnly()));

    List<String> allJvmArgs = new ArrayList<String>(jvmArgs);

    prepareEnvForTestRun(allJvmArgs, allSystemProperties, bootstrapClasspath, remoteDebugging);

    if (isRunningInBatchMode()) {
      context.getMessages().info("Running tests from " + mainModule + " matched by \'" + options.getBatchTestIncludes() + "\' pattern.");
    }
    else {
      context.getMessages().info("Starting " +
                                 (testGroups == null ? "all tests" : "tests from groups \'" + testGroups + "\'") +
                                 " from classpath of module \'" +
                                 mainModule +
                                 "\'");
    }

    String numberOfBuckets = allSystemProperties.get(TestCaseLoader.TEST_RUNNERS_COUNT_FLAG);
    if (numberOfBuckets != null) {
      context.getMessages().info("Tests from bucket " +
                                 allSystemProperties.get(TestCaseLoader.TEST_RUNNER_INDEX_FLAG) +
                                 " of " +
                                 numberOfBuckets +
                                 " will be executed");
    }

    String runtime = runtimeExecutablePath().toString();
    context.getMessages().info("Runtime: " + runtime);
    ProcessKt.runProcess(List.of(runtime, "-version"), null, context.getMessages());
    context.getMessages().info("Runtime options: " + String.valueOf(allJvmArgs));
    context.getMessages().info("System properties: " + String.valueOf(allSystemProperties));
    context.getMessages().info("Bootstrap classpath: " + String.valueOf(bootstrapClasspath));
    context.getMessages().info("Tests classpath: " + String.valueOf(testsClasspath));
    if (!envVariables.isEmpty()) {
      context.getMessages().info("Environment variables: " + String.valueOf(envVariables));
    }


    runJUnit5Engine(mainModule, allSystemProperties, allJvmArgs, envVariables, bootstrapClasspath, testsClasspath);
    notifySnapshotBuilt(allJvmArgs);
  }

  private Path runtimeExecutablePath() {
    String binJava = "bin/java";
    String binJavaExe = "bin/java.exe";
    String contentsHome = "Contents/Home";

    Path runtimeDir;
    if (options.getCustomRuntimePath() != null) {
      runtimeDir = Path.of(options.getCustomRuntimePath());
      if (!Files.isDirectory(runtimeDir)) {
        throw new IllegalStateException(
          "Custom Jre path from system property '" + TestingOptions.TEST_JRE_PROPERTY + "' is missing: " + runtimeDir);
      }
    }
    else {
      runtimeDir = context.getBundledRuntime().getHomeForCurrentOsAndArch();
    }


    if (SystemInfoRt.isWindows) {
      Path path = runtimeDir.resolve(binJavaExe);
      if (!Files.exists(path)) {
        throw new IllegalStateException("java.exe is missing: " + path);
      }

      return path;
    }


    if (SystemInfoRt.isMac) {
      if (Files.exists(runtimeDir.resolve(binJava))) {
        return runtimeDir.resolve(binJava);
      }


      if (Files.exists(runtimeDir.resolve(contentsHome).resolve(binJava))) {
        return runtimeDir.resolve(contentsHome).resolve(binJava);
      }


      throw new IllegalStateException("java executable is missing under " + runtimeDir);
    }


    if (!Files.exists(runtimeDir.resolve(binJava))) {
      throw new IllegalStateException("java executable is missing: " + runtimeDir.resolve(binJava));
    }


    return runtimeDir.resolve(binJava);
  }

  private void notifySnapshotBuilt(List<String> jvmArgs) {
    final String option = "-XX:HeapDumpPath=";
    Path file = Path.of(DefaultGroovyMethods.find(jvmArgs, new Closure<Boolean>(this, this) {
      public Boolean doCall(String it) { return it.startsWith(option); }

      public Boolean doCall() {
        return doCall(null);
      }
    }).substring(option.length()));
    if (Files.exists(file)) {
      context.notifyArtifactWasBuilt(file);
    }
  }

  @Override
  public Path createSnapshotsDirectory() {
    Path snapshotsDir = context.getPaths().getProjectHomeDir().resolve("out/snapshots");
    NioFiles.deleteRecursively(snapshotsDir);
    Files.createDirectories(snapshotsDir);
    return snapshotsDir;
  }

  @Override
  public void prepareEnvForTestRun(List<String> jvmArgs,
                                   final Map<String, String> systemProperties,
                                   List<String> classPath,
                                   boolean remoteDebugging) {
    if (jvmArgs.contains("-Djava.system.class.loader=com.intellij.util.lang.UrlClassLoader")) {
      JpsModule utilModule = context.findRequiredModule("intellij.platform.util");
      JpsJavaDependenciesEnumerator enumerator =
        JpsJavaExtensionService.dependencies(utilModule).recursively().withoutSdk().includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME);
      List<String> utilClasspath = DefaultGroovyMethods.collect(enumerator.classes().getRoots(), new Closure<String>(this, this) {
        public String doCall(File it) { return it.getAbsolutePath(); }

        public String doCall() {
          return doCall(null);
        }
      });
      classPath.addAll(DefaultGroovyMethods.minus(utilClasspath, classPath));
    }


    Path snapshotsDir = createSnapshotsDirectory();
    String hprofSnapshotFilePath = snapshotsDir.resolve("intellij-tests-oom.hprof").toString();
    List<String> defaultJvmArgs = DefaultGroovyMethods.plus(VmOptionsGenerator.getCOMMON_VM_OPTIONS(), new ArrayList<String>(
      Arrays.asList("-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=" + hprofSnapshotFilePath, "-Dkotlinx.coroutines.debug=on")));
    jvmArgs.addAll(0, defaultJvmArgs);
    if (options.getJvmMemoryOptions() != null) {
      DefaultGroovyMethods.addAll(jvmArgs, StringGroovyMethods.split(options.getJvmMemoryOptions()));
    }
    else {
      jvmArgs.addAll(new ArrayList<String>(Arrays.asList("-Xmx750m", "-Xms750m", "-Dsun.io.useCanonCaches=false")));
    }


    String tempDir = System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir"));
    Map<String, String> defaultSystemProperties = new HashMap<>(Map.of(
      "idea.platform.prefix", options.getPlatformPrefix(),
      "idea.home.path", context.getPaths().projectHome,
      "idea.config.path", "$tempDir/config".toString(),
      "idea.system.path", "$tempDir/system".toString(),
      "intellij.build.compiled.classes.archives.metadata", System.getProperty("intellij.build.compiled.classes.archives.metadata"),
      "intellij.build.compiled.classes.archive", System.getProperty("intellij.build.compiled.classes.archive"),
      (BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY), "$context.projectOutputDirectory".toString(),
      ));
    defaultSystemProperties.putAll(Map.of(
          "idea.coverage.enabled.build"                       , System.getProperty("idea.coverage.enabled.build"),
          "teamcity.buildConfName"                            , System.getProperty("teamcity.buildConfName"),
          "java.io.tmpdir"                                    , tempDir,
          "teamcity.build.tempDir"                            , tempDir,
          "teamcity.tests.recentlyFailedTests.file"           , System.getProperty("teamcity.tests.recentlyFailedTests.file"),
          "teamcity.build.branch.is_default"                  , System.getProperty("teamcity.build.branch.is_default"),
          "jna.nosys"                                         , "true",
          "file.encoding"                                     , "UTF-8",
          "io.netty.leakDetectionLevel"                       , "PARANOID"
        ));

    defaultSystemProperties.forEach((k, v) -> systemProperties.putIfAbsent(k, v));

    DefaultGroovyMethods.each(System.getProperties(), new Closure<String>(this, this) {
      public String doCall(String key, String value) {
        if (key.startsWith("pass.")) {
          systemProperties.put(key.substring("pass.".length()), value);
          return value;
        }
        return "";
      }
    });

    if (PortableCompilationCache.getCAN_BE_USED()) {
      systemProperties.put(BuildOptions.USE_COMPILED_CLASSES_PROPERTY, "true");
    }


    final Reference<Boolean> suspendDebugProcess = new Reference<Boolean>(options.getSuspendDebugProcess());
    if (options.getPerformanceTestsOnly()) {
      context.getMessages().info("Debugging disabled for performance tests");
      suspendDebugProcess.set(false);
    }
    else if (remoteDebugging) {
      context.getMessages().info("Remote debugging via TeamCity plugin is activated.");
      if (suspendDebugProcess.get()) {
        context.getMessages().warning("'intellij.build.test.debug.suspend' option is ignored while debugging via TeamCity plugin");
        suspendDebugProcess.set(false);
      }
    }
    else if (options.getDebugEnabled()) {
      String debuggerParameter =
        StringGroovyMethods.asBoolean("-agentlib:jdwp=transport=dt_socket,server=y,suspend=" + suspendDebugProcess.get())
        ? "y"
        : "n" + ",address=" + options.getDebugHost() + ":" + String.valueOf(options.getDebugPort());
      jvmArgs.add(debuggerParameter);
    }


    if (options.getEnableCausalProfiling()) {
      CausalProfilingOptions causalProfilingOptions = CausalProfilingOptions.getIMPL();
      systemProperties.put("intellij.build.test.patterns", causalProfilingOptions.getTestClass().replace(".", "\\."));
      jvmArgs.addAll(buildCausalProfilingAgentJvmArg(causalProfilingOptions));
    }


    jvmArgs.addAll(OpenedPackages.getCommandLineArguments(context));

    if (suspendDebugProcess.get()) {
      context.getMessages().info(
        "\n------------->------------- The process suspended until remote debugger connects to debug port -------------<-------------\n---------------------------------------^------^------^------^------^------^------^----------------------------------------\n");
    }
  }

  @Override
  public void runTestsSkippedInHeadlessEnvironment() {
    List<Pair<String, String>> testsSkippedInHeadlessEnvironment = context.getMessages()
      .block("Loading all tests annotated with @SkipInHeadlessEnvironment", () -> loadTestsSkippedInHeadlessEnvironment());
    testsSkippedInHeadlessEnvironment.forEach(it -> {
      options.setBatchTestIncludes(it.getFirst());
      options.setMainModule(it.getSecond());
      runTests(new ArrayList<String>(), null, null);
    });
  }

  private List<Pair<String, String>> loadTestsSkippedInHeadlessEnvironment() {
    List<Path> classpath = context.getProject().getModules().stream()
      .flatMap(it -> {
        return context.getModuleRuntimeClasspath(it, true).stream();
      })
      .distinct()
      .map(it -> Path.of(it))
      .collect(Collectors.toList());
    final UrlClassLoader classloader = UrlClassLoader.build().files(classpath).get();
    final Class<?> testAnnotation = Class.class.forName("com.intellij.testFramework.SkipInHeadlessEnvironment", false, classloader);
    return context.getProject().getModules().parallelStream().flatMap(new Closure<Stream<?>>(this, this) {
      public Stream<?> doCall(final Object module) {
        final Path root = Path.of(context.getModuleTestsOutputPath((JpsModule)module));
        if (Files.exists(root)) {
          Stream<Path> stream = Files.walk(root);
          try {
            return stream
              .filter(it -> it.toString().endsWith("Test.class"))
              .map(it -> root.relativize(it).toString())
              .filter(it -> {
                String className = FileUtilRt.getNameWithoutExtension(it).replaceAll("/", ".");
                Class<?> testClass = Class.class.forName(className, false, classloader);
                return !Modifier.isAbstract(testClass.getModifiers()) &&
                       DefaultGroovyMethods.any(testClass.getAnnotations(),
                                                new Closure<Boolean>(TestingTasksImpl.this,
                                                                     TestingTasksImpl.this) {
                                                  public Boolean doCall(Object annotation) {
                                                    return testAnnotation.isAssignableFrom(
                                                      annotation.getClass());
                                                  }
                                                });
              })
              .map(it -> {
                return Pair.create(it, ((JpsModule)module).getName());
              }).collect(Collectors.toList());
          }
          finally {
            stream.close();
          }
        }
        else {
          return Stream.empty();
        }
      }
    }).collect(Collectors.toList());
  }

  private void runInBatchMode(String mainModule,
                              final Map<String, String> systemProperties,
                              final List<String> jvmArgs,
                              final Map<String, String> envVariables,
                              final List<String> bootstrapClasspath,
                              final List<String> testClasspath) {
    String mainModuleTestsOutput = context.getModuleTestsOutputPath(context.findModule(mainModule));
    final Pattern pattern = Pattern.compile(FileUtil.convertAntToRegexp(options.getBatchTestIncludes()));
    final Path root = Path.of(mainModuleTestsOutput);

    List<Path> testClasses = IOGroovyMethods.withCloseable(Files.walk(root), new Closure<List<Path>>(this, this) {
      public List<Path> doCall(Stream<Path> stream) {
        return stream
          .filter(path -> pattern.matcher(root.relativize(path).toString()).matches())
          .collect(Collectors.toList());
      }
    });
    if (testClasses.size() == 0) {
      context.getMessages().error("No tests were found in " + String.valueOf(root) + " with " + String.valueOf(pattern));
    }

    final Reference<Boolean> noTestsInAllClasses = new Reference<Boolean>(true);
    testClasses.forEach(new Closure(this, this) {
      public Object doCall(Path path) {
        String qName = FileUtilRt.getNameWithoutExtension(root.relativize(path).toString()).replaceAll("/", ".");
        List<Path> files = new ArrayList<Path>(testClasspath.size());
        for (String p : testClasspath) {
          files.add(Path.of(p));
        }


        try {
          Boolean noTests = true;
          UrlClassLoader loader = UrlClassLoader.build().files(files).get();
          Class<?> aClazz = Class.class.forName(qName, false, loader);
          Class<?> testAnnotation = Class.class.forName("org.junit.Test", false, loader);
          for (Method m : aClazz.getDeclaredMethods()) {
            if (m.isAnnotationPresent(DefaultGroovyMethods.asType(testAnnotation, Class.class)) && Modifier.isPublic(m.getModifiers())) {
              int exitCode =
                runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath, qName, m.getName());
              noTests = DefaultGroovyMethods.and(noTests, exitCode == NO_TESTS_ERROR);
            }
          }


          if (noTests) {
            int exitCode3 = runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath, qName, null);
            noTests = DefaultGroovyMethods.and(noTests, exitCode3 == NO_TESTS_ERROR);
          }


          return setGroovyRef(noTestsInAllClasses, DefaultGroovyMethods.and(noTestsInAllClasses.get(), noTests));
        }
        catch (Throwable e) {
          throw new RuntimeException("Failed to process " + qName, e);
        }
      };
    });

    if (noTestsInAllClasses.get()) {
      context.getMessages().error("No tests were found in " + mainModule);
    }
  }

  private void runJUnit5Engine(String mainModule,
                               Map<String, String> systemProperties,
                               List<String> jvmArgs,
                               Map<String, String> envVariables,
                               List<String> bootstrapClasspath,
                               List<String> testClasspath) {
    if (isRunningInBatchMode()) {
      context.getMessages().info("Running tests in batch mode including " + options.getBatchTestIncludes());
      runInBatchMode(mainModule, systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath);
    }
    else {
      context.getMessages().info("Run junit 5 tests");
      int exitCode5 = runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath, null, null);
      context.getMessages().info("Finish junit 5 task");

      context.getMessages().info("Run junit 3 tests");
      int exitCode3 =
        runJUnit5Engine(systemProperties, jvmArgs, envVariables, bootstrapClasspath, testClasspath, options.getBootstrapSuite(), null);
      context.getMessages().info("Finish junit 3 task");

      if (exitCode5 == NO_TESTS_ERROR && exitCode3 == NO_TESTS_ERROR) {
        context.getMessages().error("No tests were found in the configuration");
      }
    }
  }

  private int runJUnit5Engine(Map<String, String> systemProperties,
                              List<String> jvmArgs,
                              Map<String, String> envVariables,
                              List<String> bootstrapClasspath,
                              List<String> testClasspath,
                              String suiteName,
                              String methodName) {
    final List<String> args = new ArrayList<String>();
    args.add("-classpath");
    List<String> classpath = new ArrayList<String>(bootstrapClasspath);

    for (String libName : List.of("JUnit5", "JUnit5Launcher", "JUnit5Vintage", "JUnit5Jupiter")) {
      for (File library : context.getProjectModel().getProject().getLibraryCollection().findLibrary(libName)
        .getFiles(JpsOrderRootType.COMPILED)) {
        classpath.add(library.getAbsolutePath());
      }
    }


    if (!isBootstrapSuiteDefault() || isRunningInBatchMode() || suiteName == null) {
      classpath.addAll(testClasspath);
    }

    args.add(DefaultGroovyMethods.join(classpath, File.pathSeparator));
    args.addAll(jvmArgs);

    //noinspection SpellCheckingInspection
    args.add("-Dintellij.build.test.runner=junit5");

    systemProperties.forEach(new BiConsumer<String, String>() {
      @Override
      public void accept(String k, String v) {
        if (v != null) {
          args.add("-D" + k + "=" + v);
        }
      }
    });

    String runner = suiteName == null ? "com.intellij.tests.JUnit5AllRunner" : "com.intellij.tests.JUnit5Runner";
    args.add(runner);
    if (suiteName != null) {
      args.add(suiteName);
    }

    if (methodName != null) {
      args.add(methodName);
    }

    File argFile = CommandLineWrapperUtil.createArgumentFile(args, Charset.defaultCharset());
    String runtime = (String)runtimeExecutablePath();
    context.getMessages().info("Starting tests on runtime " + runtime);
    ProcessBuilder builder = new ProcessBuilder(runtime, "@" + argFile.getAbsolutePath());
    builder.environment().putAll(envVariables);
    final Process exec = builder.start();

    Thread errorReader = new Thread(createInputReader(exec.getErrorStream(), System.err), "Read forked error output");
    errorReader.start();

    Thread outputReader = new Thread(createInputReader(exec.getInputStream(), System.out), "Read forked output");
    outputReader.start();

    int exitCode = exec.waitFor();

    errorReader.join(360_000);
    outputReader.join(360_000);
    if (exitCode != 0 && exitCode != NO_TESTS_ERROR) {
      context.getMessages().error("Tests failed with exit code " + String.valueOf(exitCode));
    }

    return ((int)(exitCode));
  }

  private Runnable createInputReader(final InputStream inputStream, final PrintStream outputStream) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          final BufferedReader inputReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
          try {
            while (true) {
              String line = inputReader.readLine();
              if (line == null) break;
              outputStream.println(line);
            }
          }
          finally {
            inputReader.close();
          }
        }
        catch (UnsupportedEncodingException ignored) {
        }
        catch (IOException e) {
          context.getMessages().error(e.getMessage(), e);
        }
      }
    };
  }

  protected boolean isBootstrapSuiteDefault() {
    return options.getBootstrapSuite().equals(TestingOptions.BOOTSTRAP_SUITE_DEFAULT);
  }

  protected boolean isRunningInBatchMode() {
    return options.getBatchTestIncludes() != null &&
           options.getTestPatterns() == null &&
           options.getTestConfigurations() == null &&
           options.getTestGroups().equals(TestingOptions.ALL_EXCLUDE_DEFINED_GROUP);
  }

  private List<String> buildCausalProfilingAgentJvmArg(CausalProfilingOptions options) {
    List<String> causalProfilingJvmArgs = new ArrayList<String>();

    String causalProfilerAgentName = SystemInfoRt.isLinux || SystemInfoRt.isMac ? "liblagent.so" : null;
    if (causalProfilerAgentName != null) {
      String agentArgs = options.buildAgentArgsString();
      if (agentArgs != null) {
        DefaultGroovyMethods.leftShift(causalProfilingJvmArgs, "-agentpath:" +
                                                               System.getProperty("teamcity.build.checkoutDir") +
                                                               "/" +
                                                               causalProfilerAgentName +
                                                               "=" +
                                                               agentArgs.toString());
      }
      else {
        context.getMessages().info("Could not find agent options");
      }
    }
    else {
      context.getMessages().info("Causal profiling is supported for Linux and Mac only");
    }


    return causalProfilingJvmArgs;
  }

  protected final CompilationContext context;
  protected final TestingOptions options;
  private static final int NO_TESTS_ERROR = 42;

  final private static class MyTraceFileUploader extends TraceFileUploader {
    public MyTraceFileUploader(@NotNull String serverUrl, @Nullable String token, BuildMessages messages) {
      super(serverUrl, token);
      this.messages = messages;
    }

    @Override
    protected void log(String message) {
      this.messages.info(message);
    }

    private final BuildMessages messages;
  }

  private static <T> T setGroovyRef(Reference<T> ref, T newValue) {
    ref.set(newValue);
    return newValue;
  }
}

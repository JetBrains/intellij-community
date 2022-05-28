// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import com.intellij.util.SystemProperties;

public class TestingOptions {
  public String getTestGroups() {
    return testGroups;
  }

  public void setTestGroups(String testGroups) {
    this.testGroups = testGroups;
  }

  public String getTestPatterns() {
    return testPatterns;
  }

  public void setTestPatterns(String testPatterns) {
    this.testPatterns = testPatterns;
  }

  public String getTestConfigurations() {
    return testConfigurations;
  }

  public void setTestConfigurations(String testConfigurations) {
    this.testConfigurations = testConfigurations;
  }

  public String getPlatformPrefix() {
    return platformPrefix;
  }

  public void setPlatformPrefix(String platformPrefix) {
    this.platformPrefix = platformPrefix;
  }

  public boolean getDebugEnabled() {
    return debugEnabled;
  }

  public boolean isDebugEnabled() {
    return debugEnabled;
  }

  public void setDebugEnabled(boolean debugEnabled) {
    this.debugEnabled = debugEnabled;
  }

  public String getDebugHost() {
    return debugHost;
  }

  public void setDebugHost(String debugHost) {
    this.debugHost = debugHost;
  }

  public int getDebugPort() {
    return debugPort;
  }

  public void setDebugPort(int debugPort) {
    this.debugPort = debugPort;
  }

  public boolean getSuspendDebugProcess() {
    return suspendDebugProcess;
  }

  public boolean isSuspendDebugProcess() {
    return suspendDebugProcess;
  }

  public void setSuspendDebugProcess(boolean suspendDebugProcess) {
    this.suspendDebugProcess = suspendDebugProcess;
  }

  public String getJvmMemoryOptions() {
    return jvmMemoryOptions;
  }

  public void setJvmMemoryOptions(String jvmMemoryOptions) {
    this.jvmMemoryOptions = jvmMemoryOptions;
  }

  public String getMainModule() {
    return mainModule;
  }

  public void setMainModule(String mainModule) {
    this.mainModule = mainModule;
  }

  public String getBootstrapSuite() {
    return bootstrapSuite;
  }

  public void setBootstrapSuite(String bootstrapSuite) {
    this.bootstrapSuite = bootstrapSuite;
  }

  public String getCustomRuntimePath() {
    return customRuntimePath;
  }

  public void setCustomRuntimePath(String customRuntimePath) {
    this.customRuntimePath = customRuntimePath;
  }

  public boolean getTestDiscoveryEnabled() {
    return testDiscoveryEnabled;
  }

  public boolean isTestDiscoveryEnabled() {
    return testDiscoveryEnabled;
  }

  public void setTestDiscoveryEnabled(boolean testDiscoveryEnabled) {
    this.testDiscoveryEnabled = testDiscoveryEnabled;
  }

  public String getTestDiscoveryTraceFilePath() {
    return testDiscoveryTraceFilePath;
  }

  public void setTestDiscoveryTraceFilePath(String testDiscoveryTraceFilePath) {
    this.testDiscoveryTraceFilePath = testDiscoveryTraceFilePath;
  }

  public String getTestDiscoveryIncludePatterns() {
    return testDiscoveryIncludePatterns;
  }

  public void setTestDiscoveryIncludePatterns(String testDiscoveryIncludePatterns) {
    this.testDiscoveryIncludePatterns = testDiscoveryIncludePatterns;
  }

  public String getTestDiscoveryExcludePatterns() {
    return testDiscoveryExcludePatterns;
  }

  public void setTestDiscoveryExcludePatterns(String testDiscoveryExcludePatterns) {
    this.testDiscoveryExcludePatterns = testDiscoveryExcludePatterns;
  }

  public String getBeforeRunProjectArtifacts() {
    return beforeRunProjectArtifacts;
  }

  public void setBeforeRunProjectArtifacts(String beforeRunProjectArtifacts) {
    this.beforeRunProjectArtifacts = beforeRunProjectArtifacts;
  }

  public boolean getEnableCausalProfiling() {
    return enableCausalProfiling;
  }

  public boolean isEnableCausalProfiling() {
    return enableCausalProfiling;
  }

  public void setEnableCausalProfiling(boolean enableCausalProfiling) {
    this.enableCausalProfiling = enableCausalProfiling;
  }

  public String getBatchTestIncludes() {
    return batchTestIncludes;
  }

  public void setBatchTestIncludes(String batchTestIncludes) {
    this.batchTestIncludes = batchTestIncludes;
  }

  public boolean getPerformanceTestsOnly() {
    return performanceTestsOnly;
  }

  public boolean isPerformanceTestsOnly() {
    return performanceTestsOnly;
  }

  public void setPerformanceTestsOnly(boolean performanceTestsOnly) {
    this.performanceTestsOnly = performanceTestsOnly;
  }

  public boolean getFailFast() {
    return failFast;
  }

  public boolean isFailFast() {
    return failFast;
  }

  public void setFailFast(boolean failFast) {
    this.failFast = failFast;
  }

  /**
   * Semicolon-separated names of test groups tests from which should be executed, by default all tests will be executed.
   * <p> Test groups are defined in testGroups.properties files and there is an implicit 'ALL_EXCLUDE_DEFINED' group for tests which aren't
   * included into any group and 'ALL' group for all tests. By default 'ALL_EXCLUDE_DEFINED' group is used. </p>
   */
  private String testGroups = System.getProperty("intellij.build.test.groups", OLD_TEST_GROUP);
  /**
   * Semicolon-separated patterns for test class names which need to be executed. Wildcard '*' is supported. If this option is specified,
   * {@link #testGroups} will be ignored.
   */
  private String testPatterns = System.getProperty("intellij.build.test.patterns", OLD_TEST_PATTERNS);
  /**
   * Semicolon-separated names of JUnit run configurations in the project which need to be executed. If this option is specified,
   * {@link #testGroups}, {@link #testPatterns} and {@link #mainModule} will be ignored.
   */
  private String testConfigurations = System.getProperty("intellij.build.test.configurations");
  /**
   * Specifies components from which product will be used to run tests, by default IDEA Ultimate will be used.
   */
  private String platformPrefix = System.getProperty("intellij.build.test.platform.prefix", OLD_PLATFORM_PREFIX);
  /**
   * Enables debug for testing process
   */
  private boolean debugEnabled = SystemProperties.getBooleanProperty("intellij.build.test.debug.enabled", true);
  /**
   * Specifies address on which the testing process will listen for connections, by default a localhost will be used.
   */
  private String debugHost = System.getProperty("intellij.build.test.debug.host", "localhost");
  /**
   * Specifies port on which the testing process will listen for connections, by default a random port will be used.
   */
  private int debugPort = SystemProperties.getIntProperty("intellij.build.test.debug.port", OLD_DEBUG_PORT);
  /**
   * If {@code true} to suspend the testing process until a debugger connects to it.
   */
  private boolean suspendDebugProcess = SystemProperties.getBooleanProperty("intellij.build.test.debug.suspend", OLD_SUSPEND_DEBUG_PROCESS);
  /**
   * Custom JVM memory options (e.g. -Xmx) for the testing process.
   */
  private String jvmMemoryOptions = System.getProperty("intellij.build.test.jvm.memory.options", OLD_JVM_MEMORY_OPTIONS);
  /**
   * Specifies a module which classpath will be used to search the test classes.
   */
  private String mainModule = System.getProperty("intellij.build.test.main.module", OLD_MAIN_MODULE);
  /**
   * Specifies a custom test suite, com.intellij.tests.BootstrapTests is using by default.
   */
  private String bootstrapSuite = System.getProperty("intellij.build.test.bootstrap.suite", BOOTSTRAP_SUITE_DEFAULT);
  /**
   * Specifies path to runtime which will be used to run tests.
   * By default {@code runtimeBuild} from gradle.properties will be used.
   * If it is missing then tests will run under the same runtime which is used to run the build scripts.
   */
  private String customRuntimePath = System.getProperty(TEST_JRE_PROPERTY);
  /**
   * Enables capturing traces with IntelliJ test discovery agent.
   * This agent captures lightweight coverage during your testing session
   * and allows to rerun only corresponding tests for desired method or class in your project.
   * <p>
   * For the further information please see <a href="https://github.com/jetbrains/intellij-coverage"/>IntelliJ Coverage repository</a>.
   */
  private boolean testDiscoveryEnabled = SystemProperties.getBooleanProperty("intellij.build.test.discovery.enabled", false);
  /**
   * Specifies a path to the trace file for IntelliJ test discovery agent.
   */
  private String testDiscoveryTraceFilePath = System.getProperty("intellij.build.test.discovery.trace.file");
  /**
   * Specifies a list of semicolon separated include class patterns for IntelliJ test discovery agent.
   */
  private String testDiscoveryIncludePatterns = System.getProperty("intellij.build.test.discovery.include.class.patterns");
  /**
   * Specifies a list of semicolon separated exclude class patterns for IntelliJ test discovery agent.
   */
  private String testDiscoveryExcludePatterns = System.getProperty("intellij.build.test.discovery.exclude.class.patterns");
  /**
   * Specifies a list of semicolon separated project artifacts that need to be built before running the tests.
   */
  private String beforeRunProjectArtifacts = System.getProperty("intellij.build.test.beforeRun.projectArtifacts");
  /**
   * If {@code true} causal profiler agent will be attached to the testing process.
   */
  private boolean enableCausalProfiling = SystemProperties.getBooleanProperty("intellij.build.test.enable.causal.profiling", false);
  /**
   * Pattern to match tests in {@link #mainModule} or default main module tests compilation outputs.
   * Tests from each matched class will be executed in a forked Runtime.
   * <p>
   * E.g. "com/intellij/util/ui/standalone/**Test.class"
   */
  private String batchTestIncludes = System.getProperty("intellij.build.test.batchTest.includes");
  private boolean performanceTestsOnly = SystemProperties.getBooleanProperty(PERFORMANCE_TESTS_ONLY_FLAG, false);
  /**
   * Terminate execution immediately if any test fails. Both build script and test JVMs are terminated.
   */
  private boolean failFast = SystemProperties.getBooleanProperty("intellij.build.test.failFast", false);
  public static final String ALL_EXCLUDE_DEFINED_GROUP = "ALL_EXCLUDE_DEFINED";
  private static final String OLD_TEST_GROUP = System.getProperty("idea.test.group", ALL_EXCLUDE_DEFINED_GROUP);
  private static final String OLD_TEST_PATTERNS = System.getProperty("idea.test.patterns");
  private static final String OLD_PLATFORM_PREFIX = System.getProperty("idea.platform.prefix");
  private static final int OLD_DEBUG_PORT = SystemProperties.getIntProperty("debug.port", 0);
  private static final boolean OLD_SUSPEND_DEBUG_PROCESS = System.getProperty("debug.suspend", "n").equals("y");
  private static final String OLD_JVM_MEMORY_OPTIONS = System.getProperty("test.jvm.memory");
  private static final String OLD_MAIN_MODULE = System.getProperty("module.to.make");
  public static final String BOOTSTRAP_SUITE_DEFAULT = "com.intellij.tests.BootstrapTests";
  public static final String PERFORMANCE_TESTS_ONLY_FLAG = "idea.performance.tests";
  public static final String TEST_JRE_PROPERTY = "intellij.build.test.jre";
}

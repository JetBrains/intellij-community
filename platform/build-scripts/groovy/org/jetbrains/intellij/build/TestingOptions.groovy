// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic

/**
 * @author nik
 */
@CompileStatic
class TestingOptions {
  /**
   * Semicolon-separated names of test groups tests from which should be executed, by default all tests will be executed.
   * <p> Test groups are defined in testGroups.properties files and there is an implicit 'ALL_EXCLUDE_DEFINED' group for tests which aren't
   * included into any group and 'ALL' group for all tests. By default 'ALL_EXCLUDE_DEFINED' group is used. </p>
   */
  String testGroups = System.getProperty("intellij.build.test.groups", OLD_TEST_GROUP)

  /**
   * Semicolon-separated patterns for test class names which need to be executed. Wildcard '*' is supported. If this option is specified,
   * {@link #testGroups} will be ignored.
   */
  String testPatterns = System.getProperty("intellij.build.test.patterns", OLD_TEST_PATTERNS)

  /**
   * Semicolon-separated names of JUnit run configurations in the project which need to be executed. If this option is specified,
   * {@link #testGroups}, {@link #testPatterns} and {@link #mainModule} will be ignored.
   */
  String testConfigurations = System.getProperty("intellij.build.test.configurations")

  /**
   * Specifies components from which product will be used to run tests, by default IDEA Ultimate will be used.
   */
  String platformPrefix = System.getProperty("intellij.build.test.platform.prefix", OLD_PLATFORM_PREFIX)

  /**
   * Specifies port on which the testing process will listen for connections, by default a random port will be used.
   */
  int debugPort = SystemProperties.getIntProperty("intellij.build.test.debug.port", OLD_DEBUG_PORT)

  /**
   * If {@code true} to suspend the testing process until a debugger connects to it.
   */
  boolean suspendDebugProcess = SystemProperties.getBooleanProperty("intellij.build.test.debug.suspend", OLD_SUSPEND_DEBUG_PROCESS)

  /**
   * Custom JVM memory options (e.g. -Xmx) for the testing process.
   */
  String jvmMemoryOptions = System.getProperty("intellij.build.test.jvm.memory.options", OLD_JVM_MEMORY_OPTIONS)

  /**
   * Specifies a module which classpath will be used to search the test classes.
   */
  String mainModule = System.getProperty("intellij.build.test.main.module", OLD_MAIN_MODULE)

  /**
   * Specifies a custom test suite, com.intellij.tests.BootstrapTests is using by default.
   */
  String bootstrapSuite = System.getProperty("intellij.build.test.bootstrap.suite", BOOTSTRAP_SUITE_DEFAULT)

  /**
   * Specifies path to JRE which will be used to run tests. By default tests run under the same JRE which is used to run the build scripts.
   */
  String customJrePath = System.getProperty("intellij.build.test.jre")

  /**
   * Enables capturing traces with IntelliJ test discovery agent.
   * This agent captures lightweight coverage during your testing session
   * and allows to rerun only corresponding tests for desired method or class in your project.
   *
   * For the further information please see <a href="https://github.com/jetbrains/intellij-coverage"/>IntelliJ Coverage repository</a>.
   */
  boolean testDiscoveryEnabled = SystemProperties.getBooleanProperty("intellij.build.test.discovery.enabled", false)

  /**
   * Specifies a path to the trace file for IntelliJ test discovery agent.
   */
  String testDiscoveryTraceFilePath = System.getProperty("intellij.build.test.discovery.trace.file")

  /**
   * Specifies a list of semicolon separated include class patterns for IntelliJ test discovery agent.
   */
  String testDiscoveryIncludePatterns = System.getProperty("intellij.build.test.discovery.include.class.patterns")

  /**
   * Specifies a list of semicolon separated exclude class patterns for IntelliJ test discovery agent.
   */
  String testDiscoveryExcludePatterns = System.getProperty("intellij.build.test.discovery.exclude.class.patterns")

  public static final String ALL_EXCLUDE_DEFINED_GROUP = "ALL_EXCLUDE_DEFINED"
  private static final String OLD_TEST_GROUP = System.getProperty("idea.test.group", ALL_EXCLUDE_DEFINED_GROUP)
  private static final String OLD_TEST_PATTERNS = System.getProperty("idea.test.patterns")
  private static final String OLD_PLATFORM_PREFIX = System.getProperty("idea.platform.prefix")
  private static final int OLD_DEBUG_PORT = SystemProperties.getIntProperty("debug.port", -1)
  private static final boolean OLD_SUSPEND_DEBUG_PROCESS = System.getProperty("debug.suspend", "n") == "y"
  private static final String OLD_JVM_MEMORY_OPTIONS = System.getProperty("test.jvm.memory")
  private static final String OLD_MAIN_MODULE = System.getProperty("module.to.make")

  public static final String BOOTSTRAP_SUITE_DEFAULT = "com.intellij.tests.BootstrapTests"
}

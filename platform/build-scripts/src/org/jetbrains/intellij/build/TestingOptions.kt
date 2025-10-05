// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.TestCaseLoader
import com.intellij.util.SystemProperties
import com.intellij.util.text.nullize
import org.jetbrains.intellij.build.TestingOptions.Companion.ALL_EXCLUDE_DEFINED_GROUP
import org.jetbrains.intellij.build.TestingOptions.Companion.BOOTSTRAP_SUITE_DEFAULT

private val OLD_TEST_GROUP = System.getProperty("idea.test.group", TestingOptions.ALL_EXCLUDE_DEFINED_GROUP)
private val OLD_TEST_PATTERNS = System.getProperty("idea.test.patterns")
private val OLD_PLATFORM_PREFIX = System.getProperty("idea.platform.prefix")
private val OLD_DEBUG_PORT = System.getProperty("debug.port")?.toIntOrNull() ?: 0
private val OLD_SUSPEND_DEBUG_PROCESS = System.getProperty("debug.suspend", "n") == "y"
private val OLD_JVM_MEMORY_OPTIONS = System.getProperty("test.jvm.memory")
private val OLD_MAIN_MODULE = System.getProperty("module.to.make")

/**
 * Options available for tests running on TeamCity.
 * If you want to run it locally, see [CommunityRunTestsBuildTarget] or [IdeaUltimateRunTestsBuildTarget].
 *
 * When running tests locally, specify the necessary options as VM arguments, e.g. `-Dintellij.build.test.groups=JAVA_TESTS`
 */
open class TestingOptions {
  companion object {
    const val ALL_EXCLUDE_DEFINED_GROUP: String = "ALL_EXCLUDE_DEFINED"
    const val BOOTSTRAP_SUITE_DEFAULT: String = "com.intellij.tests.BootstrapTests"
    const val PERFORMANCE_TESTS_ONLY_FLAG: String = "idea.performance.tests"
    const val TEST_JRE_PROPERTY: String = "intellij.build.test.jre"
    const val REDIRECT_STDOUT_TO_FILE: String = "intellij.build.test.redirectStdoutToFile"
    const val USE_ARCHIVED_COMPILED_CLASSES: String = "intellij.build.test.use.compiled.classes.archives"
  }

  /**
   * Semicolon-separated names of test groups tests from which should be executed, by default all tests will be executed.
   *
   *  Test groups are defined in testGroups.properties files and there is an implicit [ALL_EXCLUDE_DEFINED_GROUP] group for tests which aren't
   * included into any group and 'ALL' group for all tests. By default, [ALL_EXCLUDE_DEFINED_GROUP] group is used.
   */
  var testGroups: String = System.getProperty("intellij.build.test.groups").nullize(nullizeSpaces = true) ?: OLD_TEST_GROUP

  /**
   * Semicolon-separated patterns for test class names which need to be executed. Wildcard '*' is supported. If this option is specified,
   * [testGroups] will be ignored.
   */
  var testPatterns: String? = System.getProperty("intellij.build.test.patterns").nullize(nullizeSpaces = true) ?: OLD_TEST_PATTERNS

  /**
   * Semicolon-separated names of JUnit run configurations in the project which need to be executed. If this option is specified,
   * [testGroups], [testPatterns] and [mainModule] will be ignored.
   */
  var testConfigurations: String? = System.getProperty("intellij.build.test.configurations").nullize(nullizeSpaces = true)

  /**
   * Specifies components from which product will be used to run tests, by default IDEA Ultimate will be used.
   */
  var platformPrefix: String? = System.getProperty("intellij.build.test.platform.prefix", OLD_PLATFORM_PREFIX)

  /**
   * Enables debug for a testing process
   */
  var isDebugEnabled: Boolean = getBooleanProperty("intellij.build.test.debug.enabled", true)

  /**
   * Specifies address on which the testing process will listen for connections, by default a localhost will be used.
   */
  var debugHost: String = System.getProperty("intellij.build.test.debug.host", "localhost")

  /**
   * Specifies port on which the testing process will listen for connections, by default, a random port will be used.
   */
  var debugPort: Int = System.getProperty("intellij.build.test.debug.port")?.toIntOrNull() ?: OLD_DEBUG_PORT

  /**
   * If `true` to suspend the testing process until a debugger connects to it.
   */
  var isSuspendDebugProcess: Boolean = getBooleanProperty("intellij.build.test.debug.suspend", OLD_SUSPEND_DEBUG_PROCESS)

  /**
   * Custom JVM memory options (e.g. -Xmx) for the testing process.
   */
  var jvmMemoryOptions: String? = System.getProperty("intellij.build.test.jvm.memory.options", OLD_JVM_MEMORY_OPTIONS)

  /**
   * Specifies a module which classpath will be used to search the test classes.
   */
  var mainModule: String? = System.getProperty("intellij.build.test.main.module").nullize(nullizeSpaces = true) ?: OLD_MAIN_MODULE

  /**
   * Abort tests execution if [mainModule] does not match the module specified in the Run Configuration from [testConfigurations].
   */
  var validateMainModule: Boolean = System.getProperty("intellij.build.test.main.module.validate")?.toBooleanStrict() ?: false

  /**
   * Specifies a custom test suite, [BOOTSTRAP_SUITE_DEFAULT] is using by default.
   */
  var bootstrapSuite: String = System.getProperty("intellij.build.test.bootstrap.suite", BOOTSTRAP_SUITE_DEFAULT)

  /**
   * Specifies path to runtime which will be used to run tests.
   * By default `runtimeBuild` from [org.jetbrains.intellij.build.dependencies.DependenciesProperties] will be used.
   * If it is missing then tests will run under the same runtime which is used to run the build scripts.
   */
  var customRuntimePath: String? = System.getProperty(TEST_JRE_PROPERTY)

  /**
   * Collect tests coverage using [IntelliJ Coverage agent](https://github.com/jetbrains/intellij-coverage)
   */
  var enableCoverage: Boolean = getBooleanProperty("intellij.build.test.coverage.enabled", false)

  /**
   * Specifies a list of semicolon separated include class patterns for [IntelliJ Coverage agent](https://github.com/jetbrains/intellij-coverage).
   * Required if [enableCoverage] is set to `true`.
   */
  var coveredClassesPatterns: String? = System.getProperty("intellij.build.test.coverage.include.class.patterns")

  /**
   * Enables capturing traces with IntelliJ test discovery agent.
   * This agent captures lightweight coverage during your testing session
   * and allows to rerun only corresponding tests for a desired method or class in your project.
   *
   * Also see [enableCoverage].
   */
  var isTestDiscoveryEnabled: Boolean = getBooleanProperty("intellij.build.test.discovery.enabled", false)

  /**
   * Specifies a path to the trace file for IntelliJ test discovery agent.
   */
  var testDiscoveryTraceFilePath: String? = System.getProperty("intellij.build.test.discovery.trace.file")

  /**
   * Specifies a list of semicolon separated include class patterns for IntelliJ test discovery agent.
   */
  var testDiscoveryIncludePatterns: String? = System.getProperty("intellij.build.test.discovery.include.class.patterns")

  /**
   * Specifies a list of semicolon separated exclude class patterns for IntelliJ test discovery agent.
   */
  var testDiscoveryExcludePatterns: String? = System.getProperty("intellij.build.test.discovery.exclude.class.patterns")

  /**
   * Specifies a list of semicolon separated project artifacts that need to be built before running the tests.
   */
  var beforeRunProjectArtifacts: String? = System.getProperty("intellij.build.test.beforeRun.projectArtifacts")

  /**
   * If `true` causal profiler agent will be attached to the testing process.
   */
  var isEnableCausalProfiling: Boolean = getBooleanProperty("intellij.build.test.enable.causal.profiling", false)

  /**
   * Pattern to match tests in [mainModule] or default main module tests compilation outputs.
   * Tests from each matched class will be executed in a forked Runtime.
   *
   * E.g. "com/intellij/util/ui/standalone/ **Test.class"
   */
  var batchTestIncludes: String? = System.getProperty("intellij.build.test.batchTest.includes")

  /**
   * Run only whole classes/packages in forked Runtime
   * Allowed values:
   * * `false`
   * * `class`
   * * `package`
   */
  var isDedicatedTestRuntime: String = System.getProperty("intellij.build.test.dedicated.runtime", "false")

  var isPerformanceTestsOnly: Boolean = getBooleanProperty(PERFORMANCE_TESTS_ONLY_FLAG, false)

  /**
   * When running on TeamCity and this option is true, cancel the build (instead of failing it) in case
   * the build problem occurred while preparing for the test run, for example, if we failed to download
   * the compilation cache for some reason.
   */
  var isCancelBuildOnTestPreparationFailure: Boolean = getBooleanProperty("intellij.build.test.cancel.build.on.preparation.failure", false)

  /**
   * Number of attempts to run tests. Starting from the 2nd attempt only failed tests are re-run.
   */
  var attemptCount: Int = System.getProperty("intellij.build.test.attempt.count")?.toInt() ?: 1

  /**
   * @see [com.intellij.TestCaseLoader.matchesCurrentBucket]
   */
  var bucketsCount: Int = System.getProperty(TestCaseLoader.TEST_RUNNERS_COUNT_FLAG)?.toInt() ?: 1

  /**
   * @see [com.intellij.TestCaseLoader.matchesCurrentBucket]
   */
  var bucketIndex: Int = System.getProperty(TestCaseLoader.TEST_RUNNER_INDEX_FLAG)?.toInt() ?: 0

  /**
   * Whether to use jars instead of directories with classes.
   * Better together with [BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_UNPACK]
   */
  val useArchivedCompiledClasses: Boolean = getBooleanProperty(USE_ARCHIVED_COMPILED_CLASSES, false)

  /** Skip running (and collection) of JUnit5 tests */
  val shouldSkipJUnit5Tests: Boolean = SystemProperties.getBooleanProperty("intellij.build.test.skip.tests.junit5", false)

  /** Skip running (and collection) of JUnit3/4 tests */
  val shouldSkipJUnit34Tests: Boolean = SystemProperties.getBooleanProperty("intellij.build.test.skip.tests.junit34", false)

  /**
   * If `true` then a test process's stdout is redirected to a file,
   * which is then [streamed](https://www.jetbrains.com/help/teamcity/service-messages.html#Writing+the+File+into+the+Build+Log) into the TeamCity build log.
   * The resulting file will be available as a build artifact also.
   * This may be required as a workaround for the blocked TeamCity agent stdout.
   */
  val redirectStdOutToFile: Boolean = getBooleanProperty(REDIRECT_STDOUT_TO_FILE, false)
}
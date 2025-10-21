// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.impl.TestingTasksImpl
import org.jetbrains.intellij.build.impl.coverage.Coverage
import java.nio.file.Path

interface TestingTasks {
  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(context: CompilationContext, options: TestingOptions = TestingOptions()): TestingTasks {
      return TestingTasksImpl(context, options)
    }

    /**
     * Determines whether the current JVM process is a process that runs tests.
     */
    val isInTestsProcess: Boolean
      get() = System.getProperty(BOOTSTRAP_TESTCASES_PROPERTY) != null
    
    const val BOOTSTRAP_TESTCASES_PROPERTY: String = "bootstrap.testcases"
  }

  /**
   * @param defaultMainModule    the main module to be used if [TestingOptions.mainModule] is not specified
   * @param rootExcludeCondition if not `null`, tests from modules which sources are fit this predicate will be skipped
   */
  @Deprecated(message = "the `defaultMainModule` should be passed via `TestingOptions#mainModule`")
  suspend fun runTests(
    additionalJvmOptions: List<String> = emptyList(),
    additionalSystemProperties: Map<String, String> = emptyMap(),
    defaultMainModule: String? = null,
    rootExcludeCondition: ((Path) -> Boolean)? = null,
  )

  /**
   * @param rootExcludeCondition if not `null`, tests from modules which sources are fit this predicate will be skipped
   */
  suspend fun runTests(
    additionalJvmOptions: List<String> = emptyList(),
    additionalSystemProperties: Map<String, String> = emptyMap(),
    rootExcludeCondition: ((Path) -> Boolean)? = null,
  )

  /**
   * Run all tests annotated with [com.intellij.testFramework.SkipInHeadlessEnvironment]
   */
  suspend fun runTestsSkippedInHeadlessEnvironment()

  fun createSnapshotsDirectory(): Path

  /**
   *
   * Updates given jvm args, system properties and classpath with common parameters used for running tests
   * (Xmx, debugging, a config path) etc.
   *
   *
   * The values passed as parameters have priority over the default ones, added in this method.
   *
   *
   * Mutates incoming collections.
   */
  suspend fun prepareEnvForTestRun(
    jvmArgs: MutableList<String>,
    systemProperties: MutableMap<String, String>,
    classPath: MutableList<String>,
    remoteDebugging: Boolean,
    cleanSystemDir: Boolean = true,
  )

  @get:ApiStatus.Internal
  val coverage: Coverage
}

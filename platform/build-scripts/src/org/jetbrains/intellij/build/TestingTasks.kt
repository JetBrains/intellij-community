// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.TestingTasksImpl
import java.nio.file.Path

interface TestingTasks {
  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(context: CompilationContext, options: TestingOptions = TestingOptions()): TestingTasks {
      return TestingTasksImpl(context, options)
    }
  }

  /**
   * @param defaultMainModule    the main module to be used if [TestingOptions.mainModule] is not specified
   * @param rootExcludeCondition if not `null`, tests from modules which sources are fit this predicate will be skipped
   */
  fun runTests(additionalJvmOptions: List<String> = emptyList(),
               defaultMainModule: String? = null,
               rootExcludeCondition: ((Path) -> Boolean)? = null)

  /**
   * Run all tests annotated with [com.intellij.testFramework.SkipInHeadlessEnvironment]
   */
  fun runTestsSkippedInHeadlessEnvironment()

  fun createSnapshotsDirectory(): Path

  /**
   *
   * Updates given jvm args, system properties and classpath with common parameters used for running tests
   * (Xmx, debugging, config path) etc.
   *
   *
   * The values passed as parameters have priority over the default ones, added in this method.
   *
   *
   * Mutates incoming collections.
   */
  fun prepareEnvForTestRun(jvmArgs: MutableList<String>,
                           systemProperties: MutableMap<String, String>,
                           classPath: MutableList<String>,
                           remoteDebugging: Boolean)
}

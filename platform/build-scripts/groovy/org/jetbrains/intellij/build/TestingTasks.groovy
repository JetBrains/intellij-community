// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.TestingTasksImpl

import java.nio.file.Path
import java.util.function.Predicate

@CompileStatic
abstract class TestingTasks {
  /**
   * @param defaultMainModule main module to be used instead of {@link TestingOptions#mainModule} if it isn't specified
   * @param rootExcludeCondition if not {@code null} tests from modules which sources are fit this predicate will be skipped
   */
  abstract void runTests(List<String> additionalJvmOptions, String defaultMainModule, Predicate<File> rootExcludeCondition)

  abstract Path createSnapshotsDirectory()

  /**
   * <p>Updates given jvm args, system properties and classpath with common parameters used for running tests
   * (Xmx, debugging, config path) etc.
   *
   * <p>The values passed as parameters have priority over the default ones, added in this method.
   *
   * <p>Mutates incoming collections.
   */
  abstract void prepareEnvForTestRun(List<String> jvmArgs,
                                     Map<String, String> systemProperties,
                                     List<String> classPath,
                                     boolean remoteDebugging)

  static TestingTasks create(CompilationContext context, TestingOptions options = new TestingOptions()) {
    return new TestingTasksImpl(context, options)
  }
}

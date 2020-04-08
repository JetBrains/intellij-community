/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.TestingTasksImpl

import java.util.function.Predicate

@CompileStatic
abstract class TestingTasks {
  /**
   * @param defaultMainModule main module to be used instead of {@link TestingOptions#mainModule} if it isn't specified
   * @param rootExcludeCondition if not {@code null} tests from modules which sources are fit this predicate will be skipped
   */
  abstract void runTests(List<String> additionalJvmOptions, String defaultMainModule, Predicate<File> rootExcludeCondition)

  abstract File createSnapshotsDirectory()

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

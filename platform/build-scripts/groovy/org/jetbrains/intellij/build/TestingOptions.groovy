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
  String testGroup = System.getProperty("intellij.build.test.groups", oldTestGroup)

  /**
   * Semicolon-separated patterns for test class names which need to be executed. Wildcard '*' is supported.
   */
  String testPatterns = System.getProperty("intellij.build.test.patterns", oldTestPatterns)

  /**
   * Specifies components from which product will be used to run tests, by default IDEA Ultimate will be used.
   */
  String platformPrefix = System.getProperty("intellij.build.test.platform.prefix", oldPlatformPrefix)

  /**
   * Specifies port on which the testing process will listen for connections, by default a random port will be used.
   */
  int debugPort = SystemProperties.getIntProperty("intellij.build.test.debug.port", oldDebugPort)

  /**
   * If {@code true} to suspend the testing process until a debugger connects to it.
   */
  boolean suspendDebugProcess = SystemProperties.getBooleanProperty("intellij.build.test.debug.suspend", oldSuspendDebugProcess)

  /**
   * Custom JVM memory options (e.g. -Xmx) for the testing process.
   */
  String jvmMemoryOptions = System.getProperty("intellij.build.test.jvm.memory.options", oldJvmMemoryOptions)

  /**
   * Specifies a module which classpath will be used to search the test classes.
   */
  String mainModule = System.getProperty("intellij.build.test.main.module", oldMainModule)

  private String oldTestGroup = System.getProperty("idea.test.group", "ALL_EXCLUDE_DEFINED")
  private String oldTestPatterns = System.getProperty("idea.test.patterns")
  private String oldPlatformPrefix = System.getProperty("idea.platform.prefix")
  private int oldDebugPort = SystemProperties.getIntProperty("debug.port", -1)
  private boolean oldSuspendDebugProcess = System.getProperty("debug.suspend", "n") == "y"
  private String oldJvmMemoryOptions = System.getProperty("test.jvm.memory")
  private String oldMainModule = System.getProperty("module.to.make")
}

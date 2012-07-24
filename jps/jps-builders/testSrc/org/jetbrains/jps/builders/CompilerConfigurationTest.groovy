/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.jps.builders

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.CompilerConfiguration
import org.jetbrains.jps.builders.rebuild.JpsRebuildTestCase
/**
 * @author nik
 */
class CompilerConfigurationTest extends JpsRebuildTestCase {
  public void testLoadFromIpr() {
    doTest("compilerConfiguration/compilerConfiguration.ipr")
  }

  public void testLoadFromDirectory() {
    doTest("compilerConfigurationDir/.idea")
  }

  private def doTest(final String path) {
    loadProject(path, [:])
    CompilerConfiguration configuration = myProject.compilerConfiguration
    String basePath = FileUtil.toSystemIndependentName(getTestDataRootPath()) + "/" + path.substring(0, path.indexOf('/'))
    assertFalse(configuration.clearOutputDirectoryOnRebuild)
    assertFalse(configuration.addNotNullAssertions)
    assertTrue(configuration.defaultAnnotationProcessingProfile.enabled)
    assertFalse(configuration.defaultAnnotationProcessingProfile.obtainProcessorsFromClasspath)
    assertEquals("$basePath/src", configuration.defaultAnnotationProcessingProfile.processorsPath)
    assertEquals("b", configuration.defaultAnnotationProcessingProfile.processorsOptions["a"])
    assertEquals("d", configuration.defaultAnnotationProcessingProfile.processorsOptions["c"])
    assertEquals("gen", configuration.defaultAnnotationProcessingProfile.generatedSourcesDirName)
    assertFalse(configuration.excludes.isExcluded(new File("$basePath/src/nonrec/x/Y.java")))
    assertTrue(configuration.excludes.isExcluded(new File("$basePath/src/nonrec/Y.java")))
    assertTrue(configuration.excludes.isExcluded(new File("$basePath/src/rec/x/Y.java")))
    assertTrue(configuration.excludes.isExcluded(new File("$basePath/src/rec/Y.java")))
    assertTrue(configuration.excludes.isExcluded(new File("$basePath/src/A.java")))
    assertFalse(configuration.excludes.isExcluded(new File("$basePath/src/B.java")))
  }

  public void testLoadJavacSettings() {
    loadProject("resourceCopying/resourceCopying.ipr", [:]);
    Map<String, String> options = myProject.compilerConfiguration.javacOptions
    assertNotNull(options)
    assertEquals("512", options["MAXIMUM_HEAP_SIZE"])
    assertEquals("false", options["DEBUGGING_INFO"])
    assertEquals("true", options["GENERATE_NO_WARNINGS"])
  }
}

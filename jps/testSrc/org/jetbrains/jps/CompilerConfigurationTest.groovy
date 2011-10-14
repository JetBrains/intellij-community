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
package org.jetbrains.jps

/**
 * @author nik
 */
class CompilerConfigurationTest extends JpsBuildTestCase {
  public void testLoadFromIpr() {
    doTest("testData/compilerConfiguration/compilerConfiguration.ipr")
  }

  public void testLoadFromDirectory() {
    doTest("testData/compilerConfigurationDir")
  }

  private def doTest(final String path) {
    Project project = loadProject(path, [:])
    CompilerConfiguration configuration = project.compilerConfiguration
    assertFalse(configuration.clearOutputDirectoryOnRebuild)
    assertFalse(configuration.addNotNullAssertions)
    assertTrue(configuration.annotationProcessing.enabled)
    assertFalse(configuration.annotationProcessing.obtainProcessorsFromClasspath)
    assertTrue(configuration.annotationProcessing.processorsPath.endsWith("/src"))
    assertEquals("a=b c=d", configuration.annotationProcessing.processorsOptions["my.proc"])
    assertEquals("gen", configuration.annotationProcessing.processModule["compilerConfiguration"])
  }
}

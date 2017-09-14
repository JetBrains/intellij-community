/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.rebuild;

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.assertMatches
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.util.*

/**
 * @author nik
 */
abstract class JpsRebuildTestCase: JpsBuildTestCase() {
  protected val myOutputDirectory: File by lazy {
    FileUtil.createTempDirectory("jps-build-output", "")
  }

  override fun setUp() {
    super.setUp()
    addJdk("1.6");
  }

  fun doTest(projectPath: String, expectedOutput: DirectoryContentSpec) {
    doTest(projectPath, LinkedHashMap<String, String>(), expectedOutput);
  }

  fun doTest(projectPath: String, pathVariables: Map<String, String>, expectedOutput: DirectoryContentSpec) {
    loadAndRebuild(projectPath, pathVariables);
    assertOutput(myOutputDirectory.absolutePath, expectedOutput);
  }

  fun assertOutput(targetFolder: String, expectedOutput: DirectoryContentSpec) {
    File(targetFolder).assertMatches(expectedOutput)
  }

  fun loadAndRebuild(projectPath: String, pathVariables: Map<String, String>) {
    loadProject(projectPath, pathVariables);
    rebuild();
  }

  fun rebuild() {
    JpsJavaExtensionService.getInstance()!!.getOrCreateProjectExtension(myProject).outputUrl = JpsPathUtil.pathToUrl(FileUtil.toSystemIndependentName(myOutputDirectory.absolutePath));
    doBuild(CompileScopeTestBuilder.rebuild().allModules().allArtifacts()).assertSuccessful()
  }

  override fun getAdditionalPathVariables(): MutableMap<String, String> =
    hashMapOf("ARTIFACTS_OUT" to FileUtil.toSystemIndependentName(myOutputDirectory.absolutePath) + "/artifacts")

  override fun getTestDataRootPath(): String {
    return PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/output")!!.absolutePath;
  }
}
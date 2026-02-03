// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.rebuild

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.DirectoryContentSpec
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File

abstract class JpsRebuildTestCase: JpsBuildTestCase() {
  protected val myOutputDirectory: File by lazy {
    FileUtil.createTempDirectory("jps-build-output", "")
  }

  override fun setUp() {
    super.setUp()
    addJdk("1.6")
  }

  fun doTest(projectPath: String, expectedOutput: DirectoryContentSpec) {
    doTest(projectPath, LinkedHashMap<String, String>(), expectedOutput)
  }

  fun doTest(projectPath: String, pathVariables: Map<String, String>, expectedOutput: DirectoryContentSpec) {
    loadAndRebuild(projectPath, pathVariables)
    assertOutput(myOutputDirectory.absolutePath, expectedOutput)
  }

  fun loadAndRebuild(projectPath: String, pathVariables: Map<String, String>) {
    loadProject(projectPath, pathVariables)
    rebuild()
  }

  fun rebuild() {
    JpsJavaExtensionService.getInstance()!!.getOrCreateProjectExtension(myProject).outputUrl = JpsPathUtil.pathToUrl(FileUtilRt.toSystemIndependentName(myOutputDirectory.absolutePath))
    doBuild(CompileScopeTestBuilder.rebuild().allModules().allArtifacts()).assertSuccessful()
  }

  override fun getAdditionalPathVariables(): MutableMap<String, String> =
    hashMapOf("ARTIFACTS_OUT" to FileUtil.toSystemIndependentName(myOutputDirectory.absolutePath) + "/artifacts")

  override fun getTestDataRootPath(): String {
    return PathManagerEx.findFileUnderCommunityHome("jps/jps-builders/testData/output")!!.absolutePath
  }
}
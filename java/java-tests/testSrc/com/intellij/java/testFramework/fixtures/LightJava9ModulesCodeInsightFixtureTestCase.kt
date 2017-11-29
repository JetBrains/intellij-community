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
package com.intellij.java.testFramework.fixtures

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

abstract class LightJava9ModulesCodeInsightFixtureTestCase : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = MultiModuleJava9ProjectDescriptor

  override fun tearDown() {
    MultiModuleJava9ProjectDescriptor.cleanupSourceRoots()
    super.tearDown()
  }

  protected fun addFile(path: String, text: String, module: ModuleDescriptor = MAIN): VirtualFile =
    VfsTestUtil.createFile(module.root(), path, text)

  protected fun addTestFile(path: String, text: String): VirtualFile =
    VfsTestUtil.createFile(MAIN.testRoot()!!, path, text)

  /**
   * @param classNames is like <code>arrayOf("foo.api.Api", "foo.impl.Impl")</code>; the file's directory path is created based on FQN
   */
  protected fun addJavaFiles(testDirPath: String, classNames: Array<out String>, module: ModuleDescriptor = MAIN) {
    classNames.map {
      val dot = it.lastIndexOf('.')
      val name = if (dot >= 0) it.substring(dot + 1) else it

      val sourceFile = FileUtil.findFirstThatExist("$testDirPath/$name.java")
      val text = String(FileUtil.loadFileText(sourceFile!!))
      addFile("${it.replace('.', '/')}.java", text, module)
    }
  }

  protected fun moduleInfo(@Language("JAVA") text: String, module: ModuleDescriptor = MAIN) =
    addFile("module-info.java", text, module)

  protected fun doGlobalInspectionTest(testDirPath: String, toolWrapper: InspectionToolWrapper<*, *>) {
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, true, testDirPath)
  }
}
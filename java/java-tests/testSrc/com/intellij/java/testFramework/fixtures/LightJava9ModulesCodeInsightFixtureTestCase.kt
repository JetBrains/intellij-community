// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    super.tearDown()
    MultiModuleJava9ProjectDescriptor.cleanupSourceRoots()
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
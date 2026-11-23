// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.AnalysisUIOptions
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ui.InspectionResultsView
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class InspectionResultViewTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  private val ENABLED_TOOLS = listOf("UNUSED_IMPORT", "MarkedForRemoval", "Java9RedundantRequiresStatement", "GroovyUnusedAssignment")

  private var myDefaultShowStructure = false

  @BeforeEach
  fun setUp() {
    GlobalInspectionContextImpl.TESTING_VIEW = true
    InspectionProfileImpl.INIT_INSPECTIONS = true
    myDefaultShowStructure = AnalysisUIOptions.getInstance(myFixture.project).SHOW_STRUCTURE
  }

  @AfterEach
  fun tearDown() {
    GlobalInspectionContextImpl.TESTING_VIEW = false
    InspectionProfileImpl.INIT_INSPECTIONS = false
    AnalysisUIOptions.getInstance(myFixture.project).SHOW_STRUCTURE = myDefaultShowStructure
  }

  @Test
  fun testModuleInfoProblemsTree(): Unit = timeoutRunBlocking {
    java9.addFile("module-info.java", "@Deprecated(forRemoval=true) module M2 { }", ModuleDescriptor.M2)
    java9.addFile("module-info.java", "@Deprecated\nmodule some.module {\n requires M2;\n}", ModuleDescriptor.MAIN)
    withContext(Dispatchers.EDT) {
      val view = runInspections()

      updateTree(view)
      PlatformTestUtil.expandAll(view.tree)
      updateTree(view)
      PlatformTestUtil.assertTreeEqual(view.tree, """
        -Inspection Results
         -Java
          -Code maturity
           -Usage of API marked for removal
            -some.module
             'M2' is deprecated and marked for removal
          -Declaration redundancy
           -Redundant 'requires' directive in module-info
            -some.module
             Redundant directive 'requires M2'. No usages of module packages are found.
        """.trimIndent())

      view.globalInspectionContext.uiOptions.SHOW_STRUCTURE = true
      view.update()

      updateTree(view)
      PlatformTestUtil.expandAll(view.tree)
      updateTree(view)
      PlatformTestUtil.assertTreeEqual(view.tree, """
        -Inspection Results
         -Java
          -Code maturity
           -Usage of API marked for removal
            -${LightProjectDescriptor.TEST_MODULE_NAME}
             -some.module
              'M2' is deprecated and marked for removal
          -Declaration redundancy
           -Redundant 'requires' directive in module-info
            -${LightProjectDescriptor.TEST_MODULE_NAME}
             -some.module
              Redundant directive 'requires M2'. No usages of module packages are found.
        """.trimIndent())
    }
  }

  @Test
  fun testNonJavaDirectoryModuleGrouping(): Unit = timeoutRunBlocking {
    java9.addFile("xxx/yyy/ZZZ.groovy", "class ZZZ {void mmm() { int iii = 0; }}", ModuleDescriptor.M2)
    java9.addFile("foo/bar/Baz.groovy", "class Baz {void mmm() { int iii = 0; }}", ModuleDescriptor.M3)
    withContext(Dispatchers.EDT) {
      val view = runInspections()
      view.globalInspectionContext.uiOptions.SHOW_STRUCTURE = true
      view.update()
      updateTree(view)
      PlatformTestUtil.expandAll(view.tree)
      updateTree(view)
      PlatformTestUtil.assertTreeEqual(view.tree, """
        -Inspection Results
         -Groovy
          -Data flow
           -Unused assignment
            -${ModuleDescriptor.M2.moduleName}
             -${ModuleDescriptor.M2.sourceRootName}/xxx/yyy
              -ZZZ.groovy
               Assignment is not used
            -${ModuleDescriptor.M3.moduleName}
             -${ModuleDescriptor.M3.sourceRootName}/foo/bar
              -Baz.groovy
               Assignment is not used
        """.trimIndent())
    }
  }

  private fun runInspections(): InspectionResultsView {
    val scope = AnalysisScope(myFixture.project)
    val profile = InspectionProfileImpl("test")
    val toolWrappers = ENABLED_TOOLS.map { profile.getInspectionTool(it, myFixture.project)!! }
    val context = createGlobalContextForTool(scope, myFixture.project, toolWrappers)
    context.currentProfile.initInspectionTools(myFixture.project)
    context.doInspections(scope)
    do { UIUtil.dispatchAllInvocationEvents() }
    while (!context.isFinished)
    Disposer.register(myFixture.testRootDisposable) { context.close(false) }
    return context.view
  }

  private fun updateTree(view: InspectionResultsView) {
    view.dispatchTreeUpdate()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }
}

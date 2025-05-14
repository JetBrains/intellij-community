// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.AnalysisUIOptions
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ui.InspectionResultsView
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.util.ui.UIUtil

class InspectionResultViewTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  private val ENABLED_TOOLS = listOf("UNUSED_IMPORT", "MarkedForRemoval", "Java9RedundantRequiresStatement", "GroovyUnusedAssignment")

  private var myDefaultShowStructure = false

  public override fun setUp() {
    super.setUp()
    GlobalInspectionContextImpl.TESTING_VIEW = true
    InspectionProfileImpl.INIT_INSPECTIONS = true
    myDefaultShowStructure = AnalysisUIOptions.getInstance(project).SHOW_STRUCTURE
  }

  public override fun tearDown() {
    try {
      GlobalInspectionContextImpl.TESTING_VIEW = false
      InspectionProfileImpl.INIT_INSPECTIONS = false
      AnalysisUIOptions.getInstance(project).SHOW_STRUCTURE = myDefaultShowStructure
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testModuleInfoProblemsTree() {
    addFile("module-info.java", "@Deprecated(forRemoval=true) module M2 { }", ModuleDescriptor.M2)
    addFile("module-info.java", "@Deprecated\nmodule some.module {\n requires M2;\n}", ModuleDescriptor.MAIN)
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

  fun testNonJavaDirectoryModuleGrouping() {
    addFile("xxx/yyy/ZZZ.groovy", "class ZZZ {void mmm() { int iii = 0; }}", ModuleDescriptor.M2)
    addFile("foo/bar/Baz.groovy", "class Baz {void mmm() { int iii = 0; }}", ModuleDescriptor.M3)
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

  private fun runInspections(): InspectionResultsView {
    val scope = AnalysisScope(project)
    val profile = InspectionProfileImpl("test")
    val toolWrappers = ENABLED_TOOLS.map { profile.getInspectionTool(it, project)!! }
    val context = createGlobalContextForTool(scope, project, toolWrappers)
    context.currentProfile.initInspectionTools(project)
    context.doInspections(scope)
    do { UIUtil.dispatchAllInvocationEvents() }
    while (!context.isFinished)
    Disposer.register(testRootDisposable) { context.close(false) }
    return context.view
  }

  private fun updateTree(view: InspectionResultsView) {
    view.dispatchTreeUpdate()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }
}

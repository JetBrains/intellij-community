/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.junit.Assert
import java.io.IOException

class UnnecessaryModuleDependencyInspectionTest : JavaCodeInsightFixtureTestCase() {

  @Throws(Exception::class)
  fun testRequireSuperClassInDependencies() {
    addModuleDependencies()

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod1/Class1.java", "public class Class1 extends Class0 {}")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 extends Class1 {}")

    assertInspectionProducesZeroResults(GlobalInspectionToolWrapper(UnnecessaryModuleDependencyInspection()))
  }

  private fun assertInspectionProducesZeroResults(toolWrapper: InspectionToolWrapper<*, *>) {
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    val presentation = globalContext.getPresentation(toolWrapper)
    presentation.updateContent()
    Assert.assertFalse(presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate },
                       presentation.hasReportedProblems())
  }

  @Throws(IOException::class)
  private fun addModuleDependencies(): Module {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod2, mod1)
    ModuleRootModificationUtil.addDependency(mod1, myModule)
    ModuleRootModificationUtil.addDependency(mod2, myModule)
    return mod2
  }
}
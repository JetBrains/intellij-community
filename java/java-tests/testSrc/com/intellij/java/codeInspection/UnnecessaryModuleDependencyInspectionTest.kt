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
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.junit.Assert

class UnnecessaryModuleDependencyInspectionTest : JavaCodeInsightFixtureTestCase() {

  fun testRequireSuperClassInDependencies() {
    addModuleDependencies()

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod1/Class1.java", "public class Class1 extends Class0 {}")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 extends Class1 {}")

    assertInspectionProducesZeroResults()
  }

  fun testRequireSuperClassInUnusedReturnTypeOfFactory() {
    addModuleDependencies()

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod1/Class1.java", "public class Class1 extends Class0 {}")
    myFixture.addFileToProject("mod1/Factory.java", "public class Factory { public static Class1 create() {return null;}}")
    myFixture.addFileToProject("mod2/Usage.java", "public class Usage {{Factory.create();}}")

    assertInspectionProducesZeroResults()
  }

  fun testExportedLibraryThroughModuleDependency() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val lib = IntelliJProjectConfiguration.getProjectLibrary("JUnit4")
    ModuleRootModificationUtil.addModuleLibrary(myModule, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, myModule)

    myFixture.addFileToProject("mod1/MyTest1.java", "public class MyTest1 {@org.junit.Test public void test() {}}")
    assertInspectionProducesZeroResults()
  }

  fun testDeepExportedLibraryThroughModuleDependency() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    val lib = IntelliJProjectConfiguration.getProjectLibrary("JUnit4")
    ModuleRootModificationUtil.addModuleLibrary(myModule, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, myModule, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod2, mod1)

    myFixture.addFileToProject("mod2/MyTest2.java", "public class MyTest2 {@org.junit.Test public void test() {}}")
    assertInspectionProducesZeroResults()
  }

  fun testRequireSuperClassInUnusedReturnType() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    val apiMod = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "apiMod", myFixture.tempDirFixture.findOrCreateDir("apiMod"))

    ModuleRootModificationUtil.addDependency(myModule, apiMod, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, myModule, DependencyScope.COMPILE, true)

    ModuleRootModificationUtil.addDependency(mod2, mod1)
    ModuleRootModificationUtil.addDependency(mod2, apiMod)

    myFixture.addFileToProject("apiMod/I.java", "public interface I {}")
    myFixture.addClass("public class Class0 implements I {}")
    myFixture.addFileToProject("mod1/Class1.java", "public class Class1 extends Class0 { public static Class1 create() {return null;}}")
    myFixture.addFileToProject("mod2/Usage.java", "public class Usage {{I i = Class1.create();}}")

    assertInspectionProducesZeroResults()
  }

  fun testExportedDependencies() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod2, mod1)
    ModuleRootModificationUtil.addDependency(mod1, myModule, DependencyScope.COMPILE, true)

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 extends Class0 {}")
    assertInspectionProducesZeroResults()
  }

  fun testDeepExportedDependenciesWithDirectDependency() {
    val topModule = deepDepends()
    ModuleRootModificationUtil.addDependency(topModule, myModule)
    val toolWrapper: InspectionToolWrapper<*, *> = GlobalInspectionToolWrapper(UnnecessaryModuleDependencyInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    val presentation = globalContext.getPresentation(toolWrapper)
    presentation.updateContent()
    Assert.assertTrue(presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate },
                      presentation.hasReportedProblems())
    Assert.assertEquals("Module 'mod3' sources do not depend on module 'mod2' sources",
                        presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate })
  }

  fun testDuplicatedDependencies() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    val mod3 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod3", myFixture.tempDirFixture.findOrCreateDir("mod3"))
    ModuleRootModificationUtil.updateModel(mod3) {
      val contentEntry = it.contentEntries[0]
      contentEntry.removeSourceFolder(contentEntry.sourceFolders[0])
      contentEntry.addSourceFolder(myFixture.tempDirFixture.findOrCreateDir("mod3"),
                                   JavaSourceRootType.SOURCE,
                                   JpsJavaExtensionService.getInstance().createSourceRootProperties("", true))
    }

    ModuleRootModificationUtil.addDependency(mod2, mod1, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, myModule, DependencyScope.COMPILE, true)

    ModuleRootModificationUtil.addDependency(mod3, mod1)
    ModuleRootModificationUtil.addDependency(mod3, mod2)

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod3/Class3.java", "public class Class3 extends Class0 {}")
    assertInspectionProducesZeroResults()
  }

  fun testDeepExportedDependenciesNoDirectDependency() {
    deepDepends()
    assertInspectionProducesZeroResults()
  }

  private fun deepDepends() : Module {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    val mod3 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod3", myFixture.tempDirFixture.findOrCreateDir("mod3"))

    ModuleRootModificationUtil.addDependency(mod3, mod2)
    ModuleRootModificationUtil.addDependency(mod2, mod1, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, myModule, DependencyScope.COMPILE, true)

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod3/Class3.java", "public class Class3 extends Class0 {}")
    return mod3
  }

  private fun assertInspectionProducesZeroResults() {
    val toolWrapper: InspectionToolWrapper<*, *> = GlobalInspectionToolWrapper(UnnecessaryModuleDependencyInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    val presentation = globalContext.getPresentation(toolWrapper)
    presentation.updateContent()
    Assert.assertFalse(presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate },
                       presentation.hasReportedProblems())
  }

  private fun addModuleDependencies(): Module {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod2, mod1)
    ModuleRootModificationUtil.addDependency(mod1, myModule)
    ModuleRootModificationUtil.addDependency(mod2, myModule)
    return mod2
  }
}
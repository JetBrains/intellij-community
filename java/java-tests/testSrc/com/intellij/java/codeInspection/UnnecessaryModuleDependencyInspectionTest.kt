/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ui.InspectionToolPresentation
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
import org.jetbrains.annotations.NotNull
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

  fun testDependencyThrough2Paths() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(module, mod1, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(module, mod2, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(mod1, mod2, DependencyScope.COMPILE, false)


    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod1/I2.java", "interface I2 extends I1 {}")
    myFixture.addFileToProject("mod2/I1.java", "public interface I1 {}")
    myFixture.addClass("public class Class1 extends Class0 implements I1 {}")

    assertReportedProblems("Module '${module.name}' sources do not depend on module 'mod1' sources")
  }

  fun testUsageInXml() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(mod1, mod2, DependencyScope.COMPILE, false)
    

    myFixture.addClass("package a; public class Class0 {}")
    myFixture.addFileToProject("mod1/classes.xml", "<root><class name='a.Class0'/></root>")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 {{Runnable r = new Runnable() {};}}")
    assertReportedProblems("Module 'mod1' sources do not depend on module 'mod2' sources")
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
    ModuleRootModificationUtil.addModuleLibrary(module, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, module)

    myFixture.addFileToProject("mod1/MyTest1.java", "public class MyTest1 {@org.junit.Test public void test() {}}")
    assertInspectionProducesZeroResults()
  }

  fun testDeepExportedLibraryThroughModuleDependency() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    val lib = IntelliJProjectConfiguration.getProjectLibrary("JUnit4")
    ModuleRootModificationUtil.addModuleLibrary(module, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod2, mod1)

    myFixture.addFileToProject("mod2/MyTest2.java", "public class MyTest2 {@org.junit.Test public void test() {}}")
    assertInspectionProducesZeroResults()
  }

  fun testRequireSuperClassInUnusedReturnType() {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    val apiMod = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "apiMod", myFixture.tempDirFixture.findOrCreateDir("apiMod"))

    ModuleRootModificationUtil.addDependency(module, apiMod, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, true)

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
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, true)

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 extends Class0 {}")
    assertInspectionProducesZeroResults()
  }

  fun testDeepExportedDependenciesWithDirectDependency() {
    val topModule = deepDepends()
    ModuleRootModificationUtil.addDependency(topModule, module)
    assertReportedProblems("Module 'mod3' sources do not depend on module 'mod2' sources")
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
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, true)

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
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, true)

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod3/Class3.java", "public class Class3 extends Class0 {}")
    return mod3
  }

  private fun assertInspectionProducesZeroResults() {
    val presentation = getReportedProblems()
    Assert.assertFalse(presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate },
                       presentation.hasReportedProblems().toBoolean())
  }

  private fun assertReportedProblems(expectedProblems: String) {
    val presentation = getReportedProblems()
    Assert.assertTrue(presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate },
                      presentation.hasReportedProblems().toBoolean())
    Assert.assertEquals(expectedProblems,
                        presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate })
  }

  private fun getReportedProblems(): @NotNull InspectionToolPresentation {
    val toolWrapper: InspectionToolWrapper<*, *> = GlobalInspectionToolWrapper(UnnecessaryModuleDependencyInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    val presentation = globalContext.getPresentation(toolWrapper)
    presentation.updateContent()
    return presentation
  }

  private fun addModuleDependencies(): Module {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod2, mod1)
    ModuleRootModificationUtil.addDependency(mod1, module)
    ModuleRootModificationUtil.addDependency(mod2, module)
    return mod2
  }
}
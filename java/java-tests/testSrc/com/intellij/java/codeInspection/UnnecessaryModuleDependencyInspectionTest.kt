// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ui.InspectionToolPresentation
import com.intellij.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class UnnecessaryModuleDependencyInspectionTest {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())
  private val project get() = myFixture.project
  private val module get() = myFixture.module

  @Test
  fun testRequireSuperClassInDependencies(): Unit = timeoutRunBlocking {
    addModuleDependencies()

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod1/Class1.java", "public class Class1 extends Class0 {}")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 extends Class1 {}")

    assertInspectionProducesZeroResults()
  }

  @Test
  fun testDependencyThrough2Paths(): Unit = timeoutRunBlocking {
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

  @Test
  fun testUsageInXml(): Unit = timeoutRunBlocking {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(mod1, mod2, DependencyScope.COMPILE, false)
    

    myFixture.addClass("package a; public class Class0 {}")
    myFixture.addFileToProject("mod1/classes.xml", "<root><class name='a.Class0'/></root>")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 {{Runnable r = new Runnable() {};}}")
    assertReportedProblems("Module 'mod1' sources do not depend on module 'mod2' sources")
  }

  @Test
  fun testRequireSuperClassInUnusedReturnTypeOfFactory(): Unit = timeoutRunBlocking {
    addModuleDependencies()

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod1/Class1.java", "public class Class1 extends Class0 {}")
    myFixture.addFileToProject("mod1/Factory.java", "public class Factory { public static Class1 create() {return null;}}")
    myFixture.addFileToProject("mod2/Usage.java", "public class Usage {{Factory.create();}}")

    assertInspectionProducesZeroResults()
  }
  
  @Test
  fun testUnnecessaryDependencyWhenTwoModulesUseTheSameLibrary(): Unit = timeoutRunBlocking {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod1, mod2, DependencyScope.COMPILE, false)
    val lib = IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit4", "JUnit4")
    ModuleRootModificationUtil.addModuleLibrary(mod1, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addModuleLibrary(mod2, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, true)
    myFixture.addFileToProject("mod1/MyTest1.java", "public class MyTest1 {@org.junit.Test public void test() {}}")

    assertReportedProblems("Module 'mod1' sources do not depend on module 'mod2' sources")
  }

  @Test
  fun testExportedLibraryThroughModuleDependency(): Unit = timeoutRunBlocking {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val lib = IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit4", "JUnit4")
    ModuleRootModificationUtil.addModuleLibrary(module, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, module)

    myFixture.addFileToProject("mod1/MyTest1.java", "public class MyTest1 {@org.junit.Test public void test() {}}")
    assertInspectionProducesZeroResults()
  }

  @Test
  fun testDeepExportedLibraryThroughModuleDependency(): Unit = timeoutRunBlocking {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    val lib = IntelliJProjectConfiguration.getModuleLibrary("intellij.libraries.junit4", "JUnit4")
    ModuleRootModificationUtil.addModuleLibrary(module, "JUnit4", lib.classesUrls, lib.sourcesUrls, emptyList(), DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(mod2, mod1)

    myFixture.addFileToProject("mod2/MyTest2.java", "public class MyTest2 {@org.junit.Test public void test() {}}")
    assertInspectionProducesZeroResults()
  }

  @Test
  fun testRequireSuperClassInUnusedReturnType(): Unit = timeoutRunBlocking {
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

  @Test
  fun testExportedDependencies(): Unit = timeoutRunBlocking {
    val mod1 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod1", myFixture.tempDirFixture.findOrCreateDir("mod1"))
    val mod2 = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "mod2", myFixture.tempDirFixture.findOrCreateDir("mod2"))
    ModuleRootModificationUtil.addDependency(mod2, mod1)
    ModuleRootModificationUtil.addDependency(mod1, module, DependencyScope.COMPILE, true)

    myFixture.addClass("public class Class0 {}")
    myFixture.addFileToProject("mod2/Class2.java", "public class Class2 extends Class0 {}")
    assertInspectionProducesZeroResults()
  }

  @Test
  fun testDeepExportedDependenciesWithDirectDependency(): Unit = timeoutRunBlocking {
    val topModule = deepDepends()
    ModuleRootModificationUtil.addDependency(topModule, module)
    assertReportedProblems("Module 'mod3' sources do not depend on module 'mod2' sources")
  }

  @Test
  fun testDuplicatedDependencies(): Unit = timeoutRunBlocking {
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

  @Test
  fun testDeepExportedDependenciesNoDirectDependency(): Unit = timeoutRunBlocking {
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

  private suspend fun assertInspectionProducesZeroResults() {
    val presentation = getReportedProblems()
    assertFalse(presentation.hasReportedProblems().toBoolean(),
                       presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate })
  }

  private suspend fun assertReportedProblems(expectedProblems: String) {
    val presentation = getReportedProblems()
    assertTrue(presentation.hasReportedProblems().toBoolean() , "No problems were reported. Expected: ${expectedProblems}")
    assertEquals(expectedProblems, presentation.problemDescriptors.joinToString { problem -> problem.descriptionTemplate })
  }

  private suspend fun getReportedProblems(): @NotNull InspectionToolPresentation = withContext(Dispatchers.EDT) {
    val toolWrapper: InspectionToolWrapper<*, *> = GlobalInspectionToolWrapper(UnnecessaryModuleDependencyInspection())
    val scope = AnalysisScope(project)
    val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    val presentation = globalContext.getPresentation(toolWrapper)
    presentation.updateContent()
    presentation
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
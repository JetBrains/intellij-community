// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.testFramework.fixtures

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * Compile Dependencies: 'main' -> 'm2', 'main' -> 'm4', 'main' -> 'm5', 'main' -> 'm6' => 'm7', 'm6' -> 'm8'
 * Test dependencies: 'm.test' -> 'm2'
 */
object MultiModuleJava9ProjectDescriptor : DefaultLightProjectDescriptor() {
  enum class ModuleDescriptor(internal val moduleName: String, 
                              internal val rootName: String?, 
                              internal val testRootName: String?,
                              internal val resourceRootName : String? = null) {
    MAIN(TEST_MODULE_NAME, null, "test_src"),
    M2("light_idea_test_m2", "src_m2", null),
    M3("light_idea_test_m3", "src_m3", null),
    M4("light_idea_test_m4", "src_m4", null),
    M5("light_idea_test_m5", "src_m5", null),
    M6("light_idea_test_m6", "src_m6", null),
    M7("light_idea_test_m7", "src_m7", null),
    M8("light_idea_test_m8", "src_m8", null),
    M9("light_idea_test_m9", "src_m9", null, "res_m9"),
    M_TEST("light_idea_test_m_test", null, "m_test_src");

    fun root(): VirtualFile? = when {
      this === MAIN -> LightPlatformTestCase.getSourceRoot()
      rootName != null -> TempFileSystem.getInstance().findFileByPath("/${rootName}") ?: throw IllegalStateException("Cannot find /${rootName}")
      else -> null
    }

    fun testRoot(): VirtualFile? =
      if (testRootName == null) null
      else TempFileSystem.getInstance().findFileByPath("/${testRootName}") ?: throw IllegalStateException("Cannot find /${testRootName}")
    
    fun resourceRoot(): VirtualFile? =
      if (resourceRootName == null) null
      else TempFileSystem.getInstance().findFileByPath("/${resourceRootName}") ?: throw IllegalStateException("Cannot find /${resourceRootName}")
  }

  override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk9()

  override fun setUpProject(project: Project, handler: SetupHandler) {
    super.setUpProject(project, handler)

    runWriteAction {
      val main = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME)!!

      val m2 = makeModule(project, ModuleDescriptor.M2)
      ModuleRootModificationUtil.addDependency(main, m2)

      makeModule(project, ModuleDescriptor.M3)

      val m4 = makeModule(project, ModuleDescriptor.M4)
      ModuleRootModificationUtil.addDependency(main, m4)

      val m5 = makeModule(project, ModuleDescriptor.M5)
      ModuleRootModificationUtil.addDependency(main, m5)

      val m6 = makeModule(project, ModuleDescriptor.M6)
      ModuleRootModificationUtil.addDependency(main, m6)

      val m7 = makeModule(project, ModuleDescriptor.M7)
      ModuleRootModificationUtil.addDependency(m6, m7, DependencyScope.COMPILE, true)

      val m8 = makeModule(project, ModuleDescriptor.M8)
      ModuleRootModificationUtil.addDependency(m6, m8)
      
      val m9 = makeModule(project, ModuleDescriptor.M9)
      ModuleRootModificationUtil.addDependency(main, m9)

      val m_test = makeModule(project, ModuleDescriptor.M_TEST)
      ModuleRootModificationUtil.addDependency(m_test, m2, DependencyScope.TEST, false)

      val libDir = "jar://${PathManagerEx.getTestDataPath()}/codeInsight/jigsaw"
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-named-1.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-named-2.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-with-claimed-name.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-auto-1.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-auto-2.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-multi-release.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib_invalid_1_2.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-xml-bind.jar!/")

      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-xml-ws.jar!/")
      ModuleRootModificationUtil.updateModel(main) {
        val entries = it.orderEntries.toMutableList()
        entries.add(0, entries.last())  // places an upgrade module before the JDK
        entries.removeAt(entries.size - 1)
        it.rearrangeOrderEntries(entries.toTypedArray())
      }

      ModuleRootModificationUtil.addModuleLibrary(m2, "${libDir}/lib-auto-1.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(m4, "${libDir}/lib-auto-2.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(m6, "${libDir}/lib-named-2.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(m8, "lib-with-module-info", listOf("${libDir}/lib-with-module-info.jar!/"), listOf("${libDir}/lib-with-module-info-sources.zip!/src"))
    }
  }

  private fun makeModule(project: Project, descriptor: ModuleDescriptor): Module {
    val path = "${FileUtil.getTempDirectory()}/${descriptor.moduleName}.iml"
    val module = createModule(project, path)
    ModuleRootModificationUtil.updateModel(module) { configureModule(module, it, descriptor) }
    return module
  }

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) =
    configureModule(module, model, ModuleDescriptor.MAIN)

  private fun configureModule(module: Module, model: ModifiableRootModel, descriptor: ModuleDescriptor) {
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_9
    if (descriptor !== ModuleDescriptor.MAIN) {
      model.sdk = sdk
    }
    if (descriptor.rootName != null) {
      val sourceRoot = createSourceRoot(module, descriptor.rootName)
      registerSourceRoot(module.project, sourceRoot)
      model.addContentEntry(sourceRoot).addSourceFolder(sourceRoot, JavaSourceRootType.SOURCE)
    }
    if (descriptor.testRootName != null) {
      val testRoot = createSourceRoot(module, descriptor.testRootName)
      registerSourceRoot(module.project, testRoot)
      model.addContentEntry(testRoot).addSourceFolder(testRoot, JavaSourceRootType.TEST_SOURCE)
    }
    if (descriptor.resourceRootName != null) {
      val resourceRoot = createSourceRoot(module, descriptor.resourceRootName)
      registerSourceRoot(module.project, resourceRoot)
      model.addContentEntry(resourceRoot).addSourceFolder(resourceRoot, JavaResourceRootType.RESOURCE)
    }
  }

  fun cleanupSourceRoots() = runWriteAction {
    ModuleDescriptor.values().asSequence()
      .flatMap { if (it === ModuleDescriptor.MAIN) sequenceOf(it.testRoot()) else sequenceOf(it.root(), it.testRoot(), it.resourceRoot()) }
      .filterNotNull()
      .flatMap { it.children.asSequence() }
      .forEach { it.delete(this) }
  }
}

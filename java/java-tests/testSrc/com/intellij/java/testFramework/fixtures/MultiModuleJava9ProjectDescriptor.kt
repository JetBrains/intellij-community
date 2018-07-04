/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
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
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * Dependencies: 'main' -> 'm2', 'main' -> 'm4', 'main' -> 'm5', 'main' -> 'm6' => 'm7', 'm6' -> 'm8'
 */
object MultiModuleJava9ProjectDescriptor : DefaultLightProjectDescriptor() {
  enum class ModuleDescriptor(internal val moduleName: String, internal val rootName: String) {
    MAIN(TEST_MODULE_NAME, "/not_used/"),
    M2("${TEST_MODULE_NAME}_m2", "src_m2"),
    M3("${TEST_MODULE_NAME}_m3", "src_m3"),
    M4("${TEST_MODULE_NAME}_m4", "src_m4"),
    M5("${TEST_MODULE_NAME}_m5", "src_m5"),
    M6("${TEST_MODULE_NAME}_m6", "src_m6"),
    M7("${TEST_MODULE_NAME}_m7", "src_m7"),
    M8("${TEST_MODULE_NAME}_m8", "src_m8");

    fun root(): VirtualFile =
      if (this == MAIN) LightPlatformTestCase.getSourceRoot() else TempFileSystem.getInstance().findFileByPath("/$rootName")!!

    fun testRoot(): VirtualFile? =
      if (this == MAIN) TempFileSystem.getInstance().findFileByPath("/test_src")!! else null
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
      ModuleRootModificationUtil.addDependency(m6, m8, DependencyScope.COMPILE, false)

      val libDir = "jar://${PathManagerEx.getTestDataPath()}/codeInsight/jigsaw"
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-named-1.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-with-claimed-name.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-auto-1.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-auto-2.0.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib-multi-release.jar!/")
      ModuleRootModificationUtil.addModuleLibrary(main, "${libDir}/lib_invalid_1_2.jar!/")
    }
  }

  private fun makeModule(project: Project, descriptor: ModuleDescriptor): Module {
    val path = FileUtil.join(FileUtil.getTempDirectory(), "${descriptor.moduleName}.iml")
    val module = createModule(project, path)
    val sourceRoot = createSourceRoot(module, descriptor.rootName)
    createContentEntry(module, sourceRoot)
    return module
  }

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_1_9
    if (module.name == TEST_MODULE_NAME) {
      val testRoot = createSourceRoot(module, "test_src")
      registerSourceRoot(module.project, testRoot)
      model.addContentEntry(testRoot).addSourceFolder(testRoot, JavaSourceRootType.TEST_SOURCE)
    }
  }

  fun cleanupSourceRoots() = runWriteAction {
    ModuleDescriptor.values().asSequence()
      .filter { it != ModuleDescriptor.MAIN }
      .flatMap { it.root().children.asSequence() }
      .plus(ModuleDescriptor.MAIN.testRoot()!!.children.asSequence())
      .forEach { it.delete(this) }
  }
}
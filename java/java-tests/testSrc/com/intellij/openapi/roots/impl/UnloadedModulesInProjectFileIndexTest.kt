// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.IN_LIBRARY
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.NOT_IN_PROJECT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInUnloadedModule
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt(writeIntent = true)
class UnloadedModulesInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @Test
  fun `unload module`() {
    val unloadedModule = projectModel.createModule("unloaded")
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    ModuleRootModificationUtil.addContentRoot(unloadedModule, contentRoot.path)
    val file = projectModel.baseProjectDir.newVirtualFile("content/a.txt")
    fileIndex.assertInModule(file, unloadedModule, contentRoot)
    assertNull(fileIndex.getUnloadedModuleNameForFile(file))

    projectModel.setUnloadedModules("unloaded")

    fileIndex.assertInUnloadedModule(file, "unloaded", contentRoot)
    fileIndex.assertInUnloadedModule(contentRoot, "unloaded", contentRoot)

    projectModel.setUnloadedModules()
    fileIndex.assertInModule(file, projectModel.moduleManager.findModuleByName("unloaded")!!, contentRoot)
  }

  @Test
  fun `dependent unloaded modules`() {
    val unloadedModule = projectModel.createModule("unloaded")
    val main = projectModel.createModule("main")
    val util = projectModel.createModule("util")
    val common = projectModel.createModule("common")
    ModuleRootModificationUtil.addDependency(unloadedModule, main)
    ModuleRootModificationUtil.addDependency(main, util)
    ModuleRootModificationUtil.addDependency(main, common, DependencyScope.COMPILE, true)

    projectModel.setUnloadedModules("unloaded")

    assertSameElements(getDependentUnloadedModules(main), "unloaded")
    assertEmpty(getDependentUnloadedModules(util))
    assertSameElements(getDependentUnloadedModules(common), "unloaded")

    projectModel.setUnloadedModules()
    assertEmpty(getDependentUnloadedModules(main))
    assertEmpty(getDependentUnloadedModules(common))
  }

  private fun getDependentUnloadedModules(main: Module): MutableSet<String> {
    return DirectoryIndex.getInstance(projectModel.project).getDependentUnloadedModules(main)
  }

  @Test
  fun `module-level library in unloaded module`() {
    val unloaded = projectModel.createModule("unloaded")
    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    projectModel.addModuleLevelLibrary(unloaded, "lib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }
    fileIndex.assertScope(libraryRoot, IN_LIBRARY)

    projectModel.setUnloadedModules("unloaded")
    fileIndex.assertScope(libraryRoot, NOT_IN_PROJECT)

    projectModel.setUnloadedModules()
    fileIndex.assertScope(libraryRoot, IN_LIBRARY)
  }

  @Test
  fun `project-level library in unloaded module`() {
    val unloaded = projectModel.createModule("unloaded")
    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("lib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(unloaded, library)
    fileIndex.assertScope(libraryRoot, IN_LIBRARY)

    projectModel.setUnloadedModules("unloaded")
    fileIndex.assertScope(libraryRoot, NOT_IN_PROJECT)

    projectModel.setUnloadedModules()
    fileIndex.assertScope(libraryRoot, IN_LIBRARY)
  }
}
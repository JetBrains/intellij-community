// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.NOT_IN_PROJECT
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertInModule
import com.intellij.openapi.roots.impl.ProjectFileIndexScopes.assertScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@RunInEdt
class UnloadedModulesInProjectFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  private val directoryIndex
    get() = DirectoryIndex.getInstance(projectModel.project)
  
  @Test
  fun `unloaded module`() {
    val unloadedModule = projectModel.createModule("unloaded")
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    ModuleRootModificationUtil.addContentRoot(unloadedModule, contentRoot.path)
    val file = projectModel.baseProjectDir.newVirtualFile("content/a.txt")
    fileIndex.assertInModule(file, unloadedModule, contentRoot)
    runUnderModalProgressIfIsEdt {
      projectModel.moduleManager.setUnloadedModules(listOf("unloaded"))
    }
    assertFromUnloadedModule(file, "unloaded")
    assertFromUnloadedModule(contentRoot, "unloaded")
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
    runUnderModalProgressIfIsEdt {
      projectModel.moduleManager.setUnloadedModules(listOf("unloaded"))
    }
    assertSameElements(directoryIndex.getDependentUnloadedModules(main), "unloaded")
    assertEmpty(directoryIndex.getDependentUnloadedModules(util))
    assertSameElements(directoryIndex.getDependentUnloadedModules(common), "unloaded")
  }

  private fun assertFromUnloadedModule(file: VirtualFile, moduleName: String) {
    fileIndex.assertScope(file, NOT_IN_PROJECT)
    assertEquals(moduleName, directoryIndex.getInfoForFile(file).unloadedModuleName)
  }
}
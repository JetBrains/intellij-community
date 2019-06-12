// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.ModuleTestCase
import java.io.File

/**
 * @author nik
 */
class UnloadedModulesConfigurationTest : ModuleTestCase() {
  fun `test load project`() {
    val projectPath = FileUtilRt.toSystemIndependentName(File(PathManagerEx.getTestDataPath(), "moduleRootManager/unloadedModules").absolutePath)
    val project = getProjectManager().loadAndOpenProject(projectPath)!!
    try {
      val moduleManager = ModuleManager.getInstance(project)
      assertEquals(3, moduleManager.allModuleDescriptions.size)
      assertEquals(2, moduleManager.unloadedModuleDescriptions.size)

      val util = moduleManager.unloadedModuleDescriptions.find { it.name == "util" }!!
      val projectDirUrl = VfsUtilCore.pathToUrl(projectPath)
      assertEquals("$projectDirUrl/util", assertOneElement(util.contentRoots).url)
      assertEmpty(util.dependencyModuleNames)

      val dep = moduleManager.unloadedModuleDescriptions.find { it.name == "dep" }!!
      assertEquals("$projectDirUrl/dep", assertOneElement(dep.contentRoots).url)
      assertEquals("util", assertOneElement(dep.dependencyModuleNames))
    }
    finally {
      getProjectManager().forceCloseProject(project, true)
    }
  }

  fun `test set unloaded modules`() {
    val a = createModule("a")
    val b = createModule("b")
    val contentRootPath = FileUtil.toSystemIndependentName(createTempDirectory().absolutePath)
    ModuleRootModificationUtil.addContentRoot(a, contentRootPath)
    ModuleRootModificationUtil.addDependency(a, b)
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    assertNull(moduleManager.findModuleByName("a"))
    assertNotNull(moduleManager.findModuleByName("b"))

    moduleManager.setUnloadedModules(listOf("b"))
    assertEquals("b", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    val newA = moduleManager.findModuleByName("a")
    assertNotNull(newA)
    assertNull(moduleManager.findModuleByName("b"))
    assertEquals(VfsUtilCore.pathToUrl(contentRootPath), assertOneElement(ModuleRootManager.getInstance(newA!!).contentRootUrls))
  }

  fun `test add unloaded module back`() {
    val a = createModule("a")
    val aImlPath = a.moduleFilePath
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)

    runWriteAction {
      moduleManager.newModule(aImlPath, StdModuleTypes.JAVA.id)
    }
    assertEmpty(moduleManager.unloadedModuleDescriptions)
  }

  fun `test rename module to unloaded module`() {
    createModule("a")
    val b = createModule("b")
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)

    runWriteAction {
      val model = moduleManager.modifiableModel
      model.renameModule(b, "a")
      model.commit()
    }
    assertEmpty(moduleManager.unloadedModuleDescriptions)
  }

  private fun getProjectManager() = ProjectManagerEx.getInstanceEx() as ProjectManagerImpl
}
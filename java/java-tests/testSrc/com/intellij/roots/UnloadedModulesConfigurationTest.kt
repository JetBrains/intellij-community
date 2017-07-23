/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.roots

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.ModuleTestCase
import java.io.File

/**
 * @author nik
 */
class UnloadedModulesConfigurationTest : ModuleTestCase() {
  fun `test load project`() {
    val projectPath = File(PathManagerEx.getTestDataPath(), "moduleRootManager/unloadedModules").absolutePath
    val project = getProjectManager().loadAndOpenProject(projectPath)!!
    try {
      val moduleManager = ModuleManager.getInstance(project)
      assertEquals(3, moduleManager.allModuleDescriptions.size)
      assertEquals(2, moduleManager.unloadedModuleDescriptions.size)

      val util = moduleManager.unloadedModuleDescriptions.find { it.name == "util" }!!
      val projectDirUrl = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(projectPath))
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

  private fun getProjectManager() = ProjectManagerEx.getInstanceEx() as ProjectManagerImpl
}
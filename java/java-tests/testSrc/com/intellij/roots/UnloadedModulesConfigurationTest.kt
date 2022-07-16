// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots

import com.intellij.facet.FacetManager
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.PlatformTestUtil
import java.io.File
import java.nio.file.Paths

class UnloadedModulesConfigurationTest : JavaModuleTestCase() {
  fun `test load project`() {
    val projectPath = FileUtilRt.toSystemIndependentName(File(PathManagerEx.getTestDataPath(), "moduleRootManager/unloadedModules").absolutePath)
    val project = PlatformTestUtil.loadAndOpenProject(Paths.get(projectPath), testRootDisposable)
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

  fun `test set unloaded modules`() {
    val a = createModule("a")
    val b = createModule("b")
    val contentRootPath = FileUtil.toSystemIndependentName(createTempDirectory().absolutePath)
    ModuleRootModificationUtil.addContentRoot(a, contentRootPath)
    ModuleRootModificationUtil.addDependency(a, b)
    val moduleManager = ModuleManager.getInstance(project)
    runUnderModalProgressIfIsEdt {
      moduleManager.setUnloadedModules(listOf("a"))
    }
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    assertNull(moduleManager.findModuleByName("a"))
    assertNotNull(moduleManager.findModuleByName("b"))

    runUnderModalProgressIfIsEdt {
      moduleManager.setUnloadedModules(listOf("b"))
    }
    assertEquals("b", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    val newA = moduleManager.findModuleByName("a")
    assertNotNull(newA)
    assertNull(moduleManager.findModuleByName("b"))
    assertEquals(VfsUtilCore.pathToUrl(contentRootPath), assertOneElement(ModuleRootManager.getInstance(newA!!).contentRootUrls))
  }

  @TestFor(issues = ["IDEA-296840"])
  fun `test reload module and check if facet is not disposed`() {
    registerFacetType(MockFacetType(), project)
    val a = createModule("a")
    val b = createModule("b")
    val contentRootPath = FileUtil.toSystemIndependentName(createTempDirectory().absolutePath)
    ModuleRootModificationUtil.addContentRoot(a, contentRootPath)
    ModuleRootModificationUtil.addDependency(a, b)

    runWriteAction {
      FacetManager.getInstance(a).addFacet(MockFacetType.getInstance(), "myFacet", null)
    }

    val moduleManager = ModuleManager.getInstance(project)
    runUnderModalProgressIfIsEdt {
      moduleManager.setUnloadedModules(listOf("a"))
    }
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    assertNull(moduleManager.findModuleByName("a"))
    assertNotNull(moduleManager.findModuleByName("b"))

    runUnderModalProgressIfIsEdt {
      moduleManager.setUnloadedModules(listOf())
    }

    val moduleA = ModuleManager.getInstance(project).findModuleByName("a")!!
    val allFacets = FacetManager.getInstance(moduleA).allFacets

    allFacets.forEach {
      assertFalse(it.isDisposed)
    }
  }

  fun `test add unloaded module back`() {
    val a = createModule("a")
    val aImlPath = a.moduleFilePath
    val moduleManager = ModuleManager.getInstance(project)
    runUnderModalProgressIfIsEdt {
      moduleManager.setUnloadedModules(listOf("a"))
    }
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
    runUnderModalProgressIfIsEdt {
      moduleManager.setUnloadedModules(listOf("a"))
    }
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)

    runWriteAction {
      val model = moduleManager.getModifiableModel()
      model.renameModule(b, "a")
      model.commit()
    }
    assertEmpty(moduleManager.unloadedModuleDescriptions)
  }
}
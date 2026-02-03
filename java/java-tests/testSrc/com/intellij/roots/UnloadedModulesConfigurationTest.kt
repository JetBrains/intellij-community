// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots

import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.facet.FacetManager
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.idea.TestFor
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.PlatformTestUtil
import java.io.File
import java.nio.file.Paths

class UnloadedModulesConfigurationTest : JavaModuleTestCase() {
  private val unloadedModuleEntities: List<ModuleEntity>
    get() = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.entities(
      ModuleEntity::class.java).toList()
  
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
    
    assertSameElements((WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.entities(
      ModuleEntity::class.java).map { it.name }.toList(),
                       "dep", "util")
  }

  fun `test set unloaded modules`() {
    val a = createModule("a")
    val b = createModule("b")
    val contentRootPath = FileUtil.toSystemIndependentName(createTempDirectory().absolutePath)
    ModuleRootModificationUtil.addContentRoot(a, contentRootPath)
    ModuleRootModificationUtil.addDependency(a, b)
    val moduleManager = ModuleManager.getInstance(project)
    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("a"))
    }
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    assertNull(moduleManager.findModuleByName("a"))
    assertNotNull(moduleManager.findModuleByName("b"))

    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("b"))
    }
    assertEquals("b", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    val newA = moduleManager.findModuleByName("a")
    assertNotNull(newA)
    assertNull(moduleManager.findModuleByName("b"))
    assertEquals(VfsUtilCore.pathToUrl(contentRootPath), assertOneElement(ModuleRootManager.getInstance(newA!!).contentRootUrls))
    
    assertEquals("b", unloadedModuleEntities.single().name)
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
    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("a"))
    }
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
    assertNull(moduleManager.findModuleByName("a"))
    assertNotNull(moduleManager.findModuleByName("b"))

    runWithModalProgressBlocking(project, "") {
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
    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("a"))
    }
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)

    runWriteAction {
      moduleManager.newModule(aImlPath, JavaModuleType.getModuleType().id)
    }
    assertEmpty(moduleManager.unloadedModuleDescriptions)
    assertEmpty(unloadedModuleEntities)
  }
  
  fun `test rename iml file of unloaded module`() {
    val a = createModule("a")
    runInAllowSaveMode { project.save() }
    val imlFile = a.moduleFile!!
    val moduleManager = ModuleManager.getInstance(project)
    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("a"))
    }

    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)

    runWriteAction {
      imlFile.rename(this, "b.iml")
    }
    assertEquals("b", assertOneElement(moduleManager.unloadedModuleDescriptions).name)
  }

  fun `test rename module to unloaded module`() {
    createModule("a")
    val b = createModule("b")
    val moduleManager = ModuleManager.getInstance(project)
    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("a"))
    }
    assertEquals("a", assertOneElement(moduleManager.unloadedModuleDescriptions).name)

    runWriteAction {
      val model = moduleManager.getModifiableModel()
      model.renameModule(b, "a")
      model.commit()
    }
    assertEmpty(moduleManager.unloadedModuleDescriptions)
    assertEmpty(unloadedModuleEntities)
  }

  fun `test unload module with module-level libraries`() {
    val a = createModule("a")
    val root = getVirtualFile(createTempDir("module-lib"))
    ModuleRootModificationUtil.addModuleLibrary(a, "lib", listOf(root.url), emptyList())
    val moduleManager = ModuleManager.getInstance(project)
    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("a"))
    }

    val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
    assertEquals(project.name, entityStorage.entities(ModuleEntity::class.java).single().name)
    assertEmpty(entityStorage.entities(LibraryEntity::class.java).toList())

    assertEquals("a", unloadedModuleEntities.single().name)
    val unloadedStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities
    assertEquals("lib", unloadedStorage.entities(LibraryEntity::class.java).single().name)

    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf())
    }
    assertEmpty(unloadedModuleEntities)
    assertEmpty((WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.entities(
      LibraryEntity::class.java).toList())
    assertEquals("lib", WorkspaceModel.getInstance(project).currentSnapshot.entities(LibraryEntity::class.java).single().name)
  }
}
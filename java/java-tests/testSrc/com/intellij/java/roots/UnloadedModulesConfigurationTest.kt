// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightFixture
import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.facet.FacetManager
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.idea.TestFor
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

@TestApplication
class UnloadedModulesConfigurationTest {
  private val fixture by heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath())
  private val project get() = fixture.project

  private val unloadedModuleEntities: List<ModuleEntity>
    get() = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.entities(
      ModuleEntity::class.java).toList()
  
  @Test
  fun `test load project`(): Unit = timeoutRunBlocking {
    val projectPath = FileUtilRt.toSystemIndependentName(File(JavaTestUtil.getJavaTestDataPath(), "moduleRootManager/unloadedModules").absolutePath)
    val project = PlatformTestUtil.loadAndOpenProject(Paths.get(projectPath), fixture.testRootDisposable)
    val moduleManager = ModuleManager.getInstance(project)
    assertEquals(3, moduleManager.allModuleDescriptions.size)
    assertEquals(2, moduleManager.unloadedModuleDescriptions.size)

    val util = moduleManager.unloadedModuleDescriptions.find { it.name == "util" }!!
    val projectDirUrl = VfsUtilCore.pathToUrl(projectPath)
    assertEquals("$projectDirUrl/util", assertThat(util.contentRoots).singleElement().actual().url)
    assertThat(util.dependencyModuleNames).isEmpty()

    val dep = moduleManager.unloadedModuleDescriptions.find { it.name == "dep" }!!
    assertEquals("$projectDirUrl/dep", assertThat(dep.contentRoots).singleElement().actual().url)
    assertEquals("util", assertThat(dep.dependencyModuleNames).singleElement().actual())
    
    assertThat((WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.entities(
      ModuleEntity::class.java).map { it.name }.toList()).containsExactlyInAnyOrder("dep", "util")
  }

  @Test
  fun `test set unloaded modules`(): Unit = timeoutRunBlocking {
    val a = fixture.createModule("a")
    val b = fixture.createModule("b")
    val contentRootPath = FileUtil.toSystemIndependentName(fixture.createTempVirtualDir().path)
    ModuleRootModificationUtil.addContentRoot(a, contentRootPath)
    ModuleRootModificationUtil.addDependency(a, b)
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    assertEquals("a", assertThat(moduleManager.unloadedModuleDescriptions).singleElement().actual().name)
    assertNull(moduleManager.findModuleByName("a"))
    assertNotNull(moduleManager.findModuleByName("b"))

    moduleManager.setUnloadedModules(listOf("b"))
    assertEquals("b", assertThat(moduleManager.unloadedModuleDescriptions).singleElement().actual().name)
    val newA = moduleManager.findModuleByName("a")
    assertNotNull(newA)
    assertNull(moduleManager.findModuleByName("b"))
    assertEquals(VfsUtilCore.pathToUrl(contentRootPath), assertThat(ModuleRootManager.getInstance(newA!!).contentRootUrls).singleElement().actual())
    
    assertEquals("b", unloadedModuleEntities.single().name)
  }

  @TestFor(issues = ["IDEA-296840"])
  @Test
  fun `test reload module and check if facet is not disposed`(): Unit = timeoutRunBlocking {
    registerFacetType(MockFacetType(), project)
    val a = fixture.createModule("a")
    val b = fixture.createModule("b")
    val contentRootPath = FileUtil.toSystemIndependentName(fixture.createTempVirtualDir().path)
    ModuleRootModificationUtil.addContentRoot(a, contentRootPath)
    ModuleRootModificationUtil.addDependency(a, b)

    edtWriteAction {
      FacetManager.getInstance(a).addFacet(MockFacetType.getInstance(), "myFacet", null)
    }

    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    assertEquals("a", assertThat(moduleManager.unloadedModuleDescriptions).singleElement().actual().name)
    assertNull(moduleManager.findModuleByName("a"))
    assertNotNull(moduleManager.findModuleByName("b"))

    moduleManager.setUnloadedModules(listOf())

    val moduleA = ModuleManager.getInstance(project).findModuleByName("a")!!
    val allFacets = FacetManager.getInstance(moduleA).allFacets

    allFacets.forEach {
      assertFalse(it.isDisposed)
    }
  }

  @Test
  fun `test add unloaded module back`(): Unit = timeoutRunBlocking {
    val a = fixture.createModule("a")
    val aImlPath = a.moduleFilePath
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    assertEquals("a", assertThat(moduleManager.unloadedModuleDescriptions).singleElement().actual().name)

    edtWriteAction {
      moduleManager.newModule(aImlPath, JavaModuleType.getModuleType().id)
    }
    assertThat(moduleManager.unloadedModuleDescriptions).isEmpty()
    assertThat(unloadedModuleEntities).isEmpty()
  }
  
  @Test
  fun `test rename iml file of unloaded module`(): Unit = timeoutRunBlocking {
    val a = fixture.createModule("a")
    runInAllowSaveMode { project.save() }
    val imlFile = a.moduleFile!!
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))

    assertEquals("a", assertThat(moduleManager.unloadedModuleDescriptions).singleElement().actual().name)

    edtWriteAction {
      imlFile.rename(this, "b.iml")
    }
    assertEquals("b", assertThat(moduleManager.unloadedModuleDescriptions).singleElement().actual().name)
  }

  @Test
  fun `test rename module to unloaded module`(): Unit = timeoutRunBlocking {
    fixture.createModule("a")
    val b = fixture.createModule("b")
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    assertEquals("a", assertThat(moduleManager.unloadedModuleDescriptions).singleElement().actual().name)

    edtWriteAction {
      val model = moduleManager.getModifiableModel()
      model.renameModule(b, "a")
      model.commit()
    }
    assertThat(moduleManager.unloadedModuleDescriptions).isEmpty()
    assertThat(unloadedModuleEntities).isEmpty()
  }

  @Test
  fun `test unload module with module-level libraries`(): Unit = timeoutRunBlocking {
    val a = fixture.createModule("a")
    val root = fixture.createTempVirtualDir()
    ModuleRootModificationUtil.addModuleLibrary(a, "lib", listOf(root.url), emptyList())
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))

    val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
    assertEquals(project.name, entityStorage.entities(ModuleEntity::class.java).single().name)
    assertThat(entityStorage.entities(LibraryEntity::class.java).toList()).isEmpty()

    assertEquals("a", unloadedModuleEntities.single().name)
    val unloadedStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities
    assertEquals("lib", unloadedStorage.entities(LibraryEntity::class.java).single().name)

    moduleManager.setUnloadedModules(listOf())
    assertThat(unloadedModuleEntities).isEmpty()
    assertThat((WorkspaceModel.getInstance(project) as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.entities(
      LibraryEntity::class.java).toList()).isEmpty()
    assertEquals("lib", WorkspaceModel.getInstance(project).currentSnapshot.entities(LibraryEntity::class.java).single().name)
  }
}
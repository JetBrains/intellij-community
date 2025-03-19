// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@TestApplication
@RunInEdt(writeIntent = true)
class OrderEntryGraphTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @Test
  fun `module content`() {
    val module = projectModel.createModule()
    val moduleDir = projectModel.baseProjectDir.newVirtualDirectory("module")
    val srcDir = projectModel.baseProjectDir.newVirtualDirectory("module/src")
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("module/excluded")
    val excludedUnderSrcDir = projectModel.baseProjectDir.newVirtualDirectory("module/src/excluded")
    PsiTestUtil.addContentRoot(module, moduleDir)
    PsiTestUtil.addSourceRoot(module, srcDir)
    PsiTestUtil.addExcludedRoot(module, excludedDir)
    PsiTestUtil.addExcludedRoot(module, excludedUnderSrcDir)
    assertNoOrderEntries(moduleDir)
    assertModuleSourceEntryOnly(srcDir, module)
    assertNoOrderEntries(excludedDir)
    assertNoOrderEntries(excludedUnderSrcDir)

    PsiTestUtil.removeExcludedRoot(module, excludedUnderSrcDir)
    assertModuleSourceEntryOnly(excludedUnderSrcDir, module)
    
    PsiTestUtil.removeSourceRoot(module, srcDir)
    assertNoOrderEntries(srcDir)
  }

  @Test
  fun `dependency on module`() {
    val utilModule = projectModel.createModule("util")
    val utilSrcDir = projectModel.baseProjectDir.newVirtualDirectory("util/src")
    PsiTestUtil.addSourceContentToRoots(utilModule, utilSrcDir)
    val app1Module = projectModel.createModule("app1")
    ModuleRootModificationUtil.addDependency(app1Module, utilModule)
    val app2Module = projectModel.createModule("app2")
    ModuleRootModificationUtil.addDependency(app2Module, utilModule, DependencyScope.COMPILE, true)
    val testModule = projectModel.createModule("test")
    ModuleRootModificationUtil.addDependency(testModule, utilModule, DependencyScope.TEST, false)
    val runtimeModule = projectModel.createModule("runtime")
    ModuleRootModificationUtil.addDependency(runtimeModule, utilModule, DependencyScope.RUNTIME, false)
    val ext1Module = projectModel.createModule("ext1")
    ModuleRootModificationUtil.addDependency(ext1Module, app1Module)
    val ext2Module = projectModel.createModule("ext2")
    ModuleRootModificationUtil.addDependency(ext2Module, app2Module)
    
    val orderEntries = assertIncludesModuleSourceEntry(utilSrcDir, utilModule)
    assertEmpty(orderEntries.filterNot { it is ModuleOrderEntry })
    assertFromModules(orderEntries, app1Module, app2Module, testModule, runtimeModule, ext2Module)
  }

  enum class LibraryLevel { PROJECT, APPLICATION } 
  
  @ParameterizedTest
  @EnumSource(LibraryLevel::class)
  fun `dependency on library`(level: LibraryLevel) {
    val classesDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes")
    val sourceDir = projectModel.baseProjectDir.newVirtualDirectory("lib/src")
    val docDir = projectModel.baseProjectDir.newVirtualDirectory("lib/doc")
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes/excluded")
    fun setupLibrary(model: LibraryEx.ModifiableModelEx) {
      model.addRoot(classesDir, OrderRootType.CLASSES)
      model.addRoot(sourceDir, OrderRootType.SOURCES)
      model.addRoot(docDir, OrderRootType.DOCUMENTATION)
      model.addExcludedRoot(excludedDir.url)
    }
    
    val library = when (level) {
      LibraryLevel.PROJECT -> projectModel.addProjectLevelLibrary("lib", ::setupLibrary)
      LibraryLevel.APPLICATION -> projectModel.addApplicationLevelLibrary("lib", ::setupLibrary)
    }
    
    val app1Module = projectModel.createModule("app1")
    ModuleRootModificationUtil.addDependency(app1Module, library)
    val app2Module = projectModel.createModule("app2")
    ModuleRootModificationUtil.addDependency(app2Module, library, DependencyScope.COMPILE, true)
    val testModule = projectModel.createModule("test")
    ModuleRootModificationUtil.addDependency(testModule, library, DependencyScope.TEST, false)
    val runtimeModule = projectModel.createModule("runtime")
    ModuleRootModificationUtil.addDependency(runtimeModule, library, DependencyScope.RUNTIME, false)
    val ext1Module = projectModel.createModule("ext1")
    ModuleRootModificationUtil.addDependency(ext1Module, app1Module)
    val ext2Module = projectModel.createModule("ext2")
    ModuleRootModificationUtil.addDependency(ext2Module, app2Module)

    assertNoOrderEntries(excludedDir)
    assertNoOrderEntries(docDir)
    val entriesForClasses = fileIndex.getOrderEntriesForFile(classesDir)
    assertEquals(entriesForClasses, fileIndex.getOrderEntriesForFile(sourceDir))
    assertEquals(ext2Module, (entriesForClasses.filterNot { it is LibraryOrderEntry }.single() as ModuleOrderEntry).ownerModule)
    assertFromModules(entriesForClasses, app1Module, app2Module, testModule, runtimeModule, ext2Module)
  }

  @Test
  fun `dependency on module-level library`() {
    val module = projectModel.createModule("module")
    val classesDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes")
    val sourceDir = projectModel.baseProjectDir.newVirtualDirectory("lib/src")
    val docDir = projectModel.baseProjectDir.newVirtualDirectory("lib/doc")
    val excludedDir = projectModel.baseProjectDir.newVirtualDirectory("lib/classes/excluded")
    val library = projectModel.addModuleLevelLibrary(module, "lib") { model ->
      model.addRoot(classesDir, OrderRootType.CLASSES)
      model.addRoot(sourceDir, OrderRootType.SOURCES)
      model.addRoot(docDir, OrderRootType.DOCUMENTATION)
      model.addExcludedRoot(excludedDir.url)
    }
    
    val testLibraryRoot = projectModel.baseProjectDir.newVirtualDirectory("test-lib")
    val testLibrary = projectModel.addModuleLevelLibrary(module, "test-lib") {
      it.addRoot(testLibraryRoot, OrderRootType.CLASSES)
    }
    
    val runtimeLibraryRoot = projectModel.baseProjectDir.newVirtualDirectory("runtime-lib")
    val runtimeLibrary = projectModel.addModuleLevelLibrary(module, "runtime-lib") {
      it.addRoot(runtimeLibraryRoot, OrderRootType.CLASSES)
    }

    val exportedLibraryRoot = projectModel.baseProjectDir.newVirtualDirectory("exported-lib")
    val exportedLibrary = projectModel.addModuleLevelLibrary(module, "exported-lib") {
      it.addRoot(exportedLibraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.modifyModel(module) { model ->
      model.findLibraryOrderEntry(testLibrary)!!.scope = DependencyScope.TEST
      model.findLibraryOrderEntry(runtimeLibrary)!!.scope = DependencyScope.RUNTIME
      model.findLibraryOrderEntry(exportedLibrary)!!.isExported = true
      true
    }

    val depModule = projectModel.createModule("dep")
    ModuleRootModificationUtil.addDependency(depModule, module)
    
    assertNoOrderEntries(excludedDir)
    assertNoOrderEntries(docDir)
    val entriesForClasses = fileIndex.getOrderEntriesForFile(classesDir)
    assertEquals(library, (entriesForClasses.single() as LibraryOrderEntry).library)
    assertEquals(entriesForClasses, fileIndex.getOrderEntriesForFile(sourceDir))
    
    val entriesForTest = fileIndex.getOrderEntriesForFile(testLibraryRoot)
    assertEquals("test-lib", (entriesForTest.single() as LibraryOrderEntry).libraryName)
    
    val entriesForRuntime = fileIndex.getOrderEntriesForFile(runtimeLibraryRoot)
    assertEquals("runtime-lib", (entriesForRuntime.single() as LibraryOrderEntry).libraryName)
    
    val entriesForExported = fileIndex.getOrderEntriesForFile(exportedLibraryRoot).sortedBy(OrderEntry::toString)
    UsefulTestCase.assertSize(2, entriesForExported)
    val (libraryEntry, moduleEntry) = entriesForExported
    assertEquals(depModule, (moduleEntry as ModuleOrderEntry).ownerModule)
    assertEquals("exported-lib", (libraryEntry as LibraryOrderEntry).libraryName)
  }

  @Test
  fun `dependency on sdk`() {
    val classesDir = projectModel.baseProjectDir.newVirtualDirectory("sdk/classes")
    val sourceDir = projectModel.baseProjectDir.newVirtualDirectory("sdk/src")
    val docDir = projectModel.baseProjectDir.newVirtualDirectory("sdk/doc")

    val sdk = projectModel.addSdk {
      it.addRoot(classesDir, OrderRootType.CLASSES)
      it.addRoot(sourceDir, OrderRootType.SOURCES)
      it.addRoot(docDir, OrderRootType.DOCUMENTATION)
    }

    val module1 = projectModel.createModule("module1")
    ModuleRootModificationUtil.setModuleSdk(module1, sdk)
    val module2 = projectModel.createModule("module2")
    ModuleRootModificationUtil.setModuleSdk(module2, sdk)

    assertNoOrderEntries(docDir)
    val entriesForClasses = fileIndex.getOrderEntriesForFile(classesDir)
    assertEquals(entriesForClasses, fileIndex.getOrderEntriesForFile(sourceDir))
    assertEmpty(entriesForClasses.filterNot { it is JdkOrderEntry })
    assertFromModules(entriesForClasses, module1, module2)
  }
  
  @Test
  fun `inherited sdk`() {
    val classesDir = projectModel.baseProjectDir.newVirtualDirectory("sdk/classes")
    val sdk = projectModel.addSdk {
      it.addRoot(classesDir, OrderRootType.CLASSES)
    }

    runWriteAction {  ProjectRootManager.getInstance(projectModel.project).projectSdk = sdk }
    assertNoOrderEntries(classesDir)
    
    val module = projectModel.createModule("module")
    ModuleRootModificationUtil.setSdkInherited(module)
    val entriesForClasses = fileIndex.getOrderEntriesForFile(classesDir)
    assertEmpty(entriesForClasses.filterNot { it is JdkOrderEntry })
    assertFromModules(entriesForClasses, module)
  }

  @Test
  fun `library root under module root`() {
    val module = projectModel.createModule()
    val moduleRoot = projectModel.baseProjectDir.newVirtualDirectory("module")
    PsiTestUtil.addSourceContentToRoots(module, moduleRoot)
    val libraryClasses = projectModel.baseProjectDir.newVirtualDirectory("module/lib/classes")
    val librarySource = projectModel.baseProjectDir.newVirtualDirectory("module/lib/source")
    val library = projectModel.addModuleLevelLibrary(module, "lib") {
      it.addRoot(libraryClasses, OrderRootType.CLASSES)
      it.addRoot(librarySource, OrderRootType.SOURCES)
    }
    assertModuleSourceEntryOnly(moduleRoot, module)
    assertEquals(library, (assertIncludesModuleSourceEntry(librarySource, module).single() as LibraryOrderEntry).library)
    assertEquals(library, (fileIndex.getOrderEntriesForFile(libraryClasses).single() as LibraryOrderEntry).library)
  }
  
  @Test
  fun `module root under library root`() {
    val libraryClasses = projectModel.baseProjectDir.newVirtualDirectory("lib/classes")
    val librarySource = projectModel.baseProjectDir.newVirtualDirectory("lib/source")
    val library = projectModel.addModuleLevelLibrary(projectModel.createModule("lib-module"), "lib") {
      it.addRoot(libraryClasses, OrderRootType.CLASSES)
      it.addRoot(librarySource, OrderRootType.SOURCES)
    }
    
    val module = projectModel.createModule()
    val moduleRoot1 = projectModel.baseProjectDir.newVirtualDirectory("lib/classes/module")
    val moduleRoot2 = projectModel.baseProjectDir.newVirtualDirectory("lib/source/module")
    PsiTestUtil.addSourceContentToRoots(module, moduleRoot1)
    PsiTestUtil.addSourceContentToRoots(module, moduleRoot2)
    
    assertModuleSourceEntryOnly(moduleRoot1, module)
    val orderEntries = assertIncludesModuleSourceEntry(moduleRoot2, module)
    assertEquals(library, (orderEntries.single() as LibraryOrderEntry).library)
    assertEquals(library, (fileIndex.getOrderEntriesForFile(librarySource).single() as LibraryOrderEntry).library)
    assertEquals(library, (fileIndex.getOrderEntriesForFile(libraryClasses).single() as LibraryOrderEntry).library)
  }
  
  @Test
  fun `same directory as module root and library root`() {
    val libraryClasses = projectModel.baseProjectDir.newVirtualDirectory("classes")
    val librarySource = projectModel.baseProjectDir.newVirtualDirectory("source")
    val library = projectModel.addModuleLevelLibrary(projectModel.createModule("lib-module"), "lib") {
      it.addRoot(libraryClasses, OrderRootType.CLASSES)
      it.addRoot(librarySource, OrderRootType.SOURCES)
    }
    
    val module = projectModel.createModule()
    PsiTestUtil.addSourceContentToRoots(module, libraryClasses)
    PsiTestUtil.addSourceContentToRoots(module, librarySource)

    assertEquals(library, (assertIncludesModuleSourceEntry(librarySource, module).single() as LibraryOrderEntry).library)
    assertModuleSourceEntryOnly(libraryClasses, module)
  }

  private fun assertModuleSourceEntryOnly(file: VirtualFile, module: Module) {
    val otherEntries = assertIncludesModuleSourceEntry(file, module)
    assertEmpty(otherEntries)
  }
  
  private fun assertIncludesModuleSourceEntry(file: VirtualFile, module: Module): List<OrderEntry> {
    val orderEntries = fileIndex.getOrderEntriesForFile(file)
    assertEquals(module, assertOneElement(orderEntries.filterIsInstance<ModuleSourceOrderEntry>()).rootModel.module)
    return orderEntries.filterNot { it is ModuleSourceOrderEntry }
  }

  private fun assertNoOrderEntries(file: VirtualFile) {
    assertEmpty(fileIndex.getOrderEntriesForFile(file))
  }

  private fun assertFromModules(entries: List<OrderEntry>, vararg modules: Module) {
    UsefulTestCase.assertSameElements(entries.map { it.ownerModule }, *modules)
  }
}
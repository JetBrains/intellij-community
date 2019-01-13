// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.module.impl.ModuleManagerImpl
import com.intellij.openapi.module.impl.ModulePath
import com.intellij.openapi.module.impl.UnloadedModuleDescriptionImpl
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ModuleTestCase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

/**
 * @author nik
 */
class AutomaticModuleUnloaderTest : ModuleTestCase() {
  fun `test unload simple module`() = runBlocking {
    createModule("a")
    createModule("b")
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    createModule("c")

    val moduleFiles = createNewModuleFiles(listOf("d")) {}
    reloadProjectWithNewModules(moduleFiles)

    ModuleTestCase.assertSameElements(moduleManager.unloadedModuleDescriptions.map { it.name }, "a", "d")
  }

  fun `test unload modules with dependencies between them`() = runBlocking {
    createModule("a")
    createModule("b")
    doTest("a", listOf("c", "d"), { modules ->
      ModuleRootModificationUtil.updateModel(modules["c"]!!) {
        it.addModuleOrderEntry(modules["d"]!!)
      }
    },"a", "c", "d")
  }

  fun `test do not unload module if loaded module depends on it`() = runBlocking {
    createModule("a")
    val b = createModule("b")
    ModuleRootModificationUtil.updateModel(b) {
      it.addInvalidModuleEntry("d")
    }
    doTest("a", listOf("d"), {}, "a")
  }

  fun `test unload module if only unloaded module depends on it`() = runBlocking {
    val a = createModule("a")
    createModule("b")
    ModuleRootModificationUtil.updateModel(a) {
      it.addInvalidModuleEntry("d")
    }
    doTest("a", listOf("d"), {}, "a", "d")
  }

  fun `test do not unload modules if loaded module depends on them transitively`() = runBlocking {
    createModule("a")
    val b = createModule("b")
    ModuleRootModificationUtil.updateModel(b) {
      it.addInvalidModuleEntry("d")
    }

    doTest("a", listOf("c", "d"), { modules ->
      ModuleRootModificationUtil.updateModel(modules["d"]!!) {
        it.addModuleOrderEntry(modules["c"]!!)
      }
    }, "a")
  }

  fun `test unload module if loaded module transitively depends on it via previously unloaded module`() = runBlocking {
    val a = createModule("a")
    val b = createModule("b")
    ModuleRootModificationUtil.addDependency(a, b)
    ModuleRootModificationUtil.updateModel(b) {
      it.addInvalidModuleEntry("c")
    }
    doTest("b", listOf("c"), {}, "b", "c")
  }

  private suspend fun doTest(initiallyUnloaded: String,
                     newModulesName: List<String>,
                     setup: (Map<String, Module>) -> Unit,
                     vararg expectedUnloadedModules: String) {
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf(initiallyUnloaded))

    val moduleFiles = createNewModuleFiles(newModulesName, setup)
    reloadProjectWithNewModules(moduleFiles)

    ModuleTestCase.assertSameElements(moduleManager.unloadedModuleDescriptions.map { it.name }, *expectedUnloadedModules)

  }

  private suspend fun createNewModuleFiles(moduleNames: List<String>, setup: (Map<String, Module>) -> Unit): List<File> {
    val newModulesProjectDir = FileUtil.createTempDirectory("newModules", "")
    val moduleFiles = moduleNames.map { File(newModulesProjectDir, "$it.iml") }
    val projectManager = ProjectManagerEx.getInstanceEx() as ProjectManagerImpl
    val project = projectManager.createProject("newModules", newModulesProjectDir.absolutePath)!!
    try {
      val modules = runWriteAction {
        moduleFiles.map {
          ModuleManager.getInstance(project).newModule(it.absolutePath, StdModuleTypes.JAVA.id)
        }
      }
      setup(ModuleManager.getInstance(project).modules.associateBy { it.name })
      modules.forEach {
        it.stateStore.save()
      }
    }
    finally {
      projectManager.forceCloseProject(project, true)
      runWriteAction { Disposer.dispose(project) }
    }
    return moduleFiles
  }

  private suspend fun reloadProjectWithNewModules(moduleFiles: List<File>) {
    val moduleManager = ModuleManagerImpl.getInstanceImpl(myProject)
    val modulePaths = LinkedHashSet<ModulePath>()
    moduleManager.modules.forEach { it.stateStore.save() }
    moduleManager.modules.mapTo(modulePaths) { ModulePath(it.moduleFilePath, null) }
    moduleManager.unloadedModuleDescriptions.mapTo(modulePaths) { (it as UnloadedModuleDescriptionImpl).modulePath }
    moduleFiles.mapTo(modulePaths) { ModulePath(FileUtil.toSystemIndependentName(it.absolutePath), null) }
    moduleManager.loadStateFromModulePaths(modulePaths)
  }

}
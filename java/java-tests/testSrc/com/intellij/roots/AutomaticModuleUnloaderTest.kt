// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.util.io.systemIndependentPath
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@RunsInEdt
class AutomaticModuleUnloaderTest {
  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @Test
  fun `unload simple module`() {
    val project = createProject()
    createModule(project, "a")
    createModule(project, "b")
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    createModule(project, "c")

    val moduleFiles = createNewModuleFiles(listOf("d")) {}
    val newProject = reloadProjectWithNewModules(project, moduleFiles)

    assertSameElements(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }, "a", "d")
  }

  @Test
  fun `unload modules with dependencies between them`() {
    val project = createProject()
    createModule(project, "a")
    createModule(project, "b")
    doTest(project, "a", listOf("c", "d"), { modules ->
      ModuleRootModificationUtil.updateModel(modules.getValue("c")) {
        it.addModuleOrderEntry(modules.getValue("d"))
      }
    }, "a", "c", "d")
  }

  @Test
  fun `do not unload module if loaded module depends on it`() {
    val project = createProject()
    createModule(project, "a")
    val b = createModule(project, "b")
    ModuleRootModificationUtil.updateModel(b) {
      it.addInvalidModuleEntry("d")
    }
    doTest(project, "a", listOf("d"), {}, "a")
  }

  @Test
  fun `unload module if only unloaded module depends on it`() {
    val project = createProject()
    val a = createModule(project, "a")
    createModule(project, "b")
    ModuleRootModificationUtil.updateModel(a) {
      it.addInvalidModuleEntry("d")
    }
    doTest(project, "a", listOf("d"), {}, "a", "d")
  }

  @Test
  fun `do not unload modules if loaded module depends on them transitively`() {
    val project = createProject()
    createModule(project, "a")
    val b = createModule(project, "b")
    ModuleRootModificationUtil.updateModel(b) {
      it.addInvalidModuleEntry("d")
    }

    doTest(project, "a", listOf("c", "d"), { modules ->
      ModuleRootModificationUtil.updateModel(modules.getValue("d")) {
        it.addModuleOrderEntry(modules.getValue("c"))
      }
    }, "a")
  }

  @Test
  fun `unload module if loaded module transitively depends on it via previously unloaded module`() {
    val project = createProject()
    val a = createModule(project, "a")
    val b = createModule(project, "b")
    ModuleRootModificationUtil.addDependency(a, b)
    ModuleRootModificationUtil.updateModel(b) {
      it.addInvalidModuleEntry("c")
    }
    doTest(project, "b", listOf("c"), {}, "b", "c")
  }

  @Test
  fun `deleted iml file`() {
    val project = createProject()
    createModule(project, "a")
    createModule(project, "b")
    val deletedIml = createModule(project, "deleted")
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("a"))
    createModule(project, "c")

    val moduleFiles = createNewModuleFiles(listOf("d")) {}
    val deletedImlFile = File(deletedIml.moduleFilePath)
    val newProject = reloadProjectWithNewModules(project, moduleFiles) {
      deletedImlFile.delete()
    }

    assertSameElements(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }, "a", "d")
  }


  private fun doTest(project: Project,
                     initiallyUnloaded: String,
                     newModulesName: List<String>,
                     setup: (Map<String, Module>) -> Unit,
                     vararg expectedUnloadedModules: String) {
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf(initiallyUnloaded))

    val moduleFiles = createNewModuleFiles(newModulesName, setup)
    val newProject = reloadProjectWithNewModules(project, moduleFiles)

    assertSameElements(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }, *expectedUnloadedModules)
  }

  private fun createProject(): Project {
    return ProjectManagerEx.getInstanceEx().newProject(tempDir.newPath("automaticReloaderTest"), createTestOpenProjectOptions())!!
  }

  private fun createModule(project: Project, moduleName: String): Module {
    return runWriteAction { ModuleManager.getInstance(project).newModule("${project.basePath}/$moduleName.iml", "JAVA") }
  }

  private fun createNewModuleFiles(moduleNames: List<String>, setup: (Map<String, Module>) -> Unit): List<Path> {
    val newModulesProjectDir = tempDir.newPath("newModules")
    val moduleFiles = moduleNames.map { newModulesProjectDir.resolve("$it.iml") }
    val project = ProjectManagerEx.getInstanceEx().newProject(newModulesProjectDir, createTestOpenProjectOptions())!!
    try {
      runWriteAction {
        moduleFiles.map {
          ModuleManager.getInstance(project).newModule(it.toAbsolutePath().toString(), StdModuleTypes.JAVA.id)
        }
      }
      setup(ModuleManager.getInstance(project).modules.associateBy { it.name })
    }
    finally {
      saveAndCloseProject(project)
    }
    return moduleFiles
  }

  private fun saveAndCloseProject(project: Project) {
    PlatformTestUtil.saveProject(project, true)
    ProjectManagerEx.getInstanceEx().forceCloseProject(project)
  }

  private fun reloadProjectWithNewModules(project: Project, moduleFiles: List<Path>, beforeReload: () -> Unit = {}): Project {
    saveAndCloseProject(project)
    val modulesXmlFile = File(project.basePath, ".idea/modules.xml")
    val rootElement = JDOMUtil.load(modulesXmlFile)
    val moduleRootComponent = JDomSerializationUtil.findComponent(rootElement, JpsProjectLoader.MODULE_MANAGER_COMPONENT)
    val modulesTag = moduleRootComponent!!.getChild("modules")!!
    moduleFiles.forEach {
      val filePath = it.systemIndependentPath
      val fileUrl = VfsUtil.pathToUrl(filePath)
      modulesTag.addContent(Element("module").setAttribute("fileurl", fileUrl).setAttribute("filepath", filePath))
    }
    JDOMUtil.write(rootElement, modulesXmlFile)
    beforeReload()
    val reloaded = PlatformTestUtil.loadAndOpenProject(Paths.get(project.basePath!!))
    Disposer.register(disposableRule.disposable, Disposable { ProjectManagerEx.getInstanceEx().forceCloseProject(reloaded) })
    return reloaded
  }

  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()

    @ClassRule
    @JvmField
    val edtRule = EdtRule()
  }

}
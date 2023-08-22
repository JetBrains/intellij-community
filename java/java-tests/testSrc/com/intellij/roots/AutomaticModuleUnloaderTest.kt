// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.ProjectStoreOwner
import com.intellij.project.TestProjectManager
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.configurationStore.copyFilesAndReloadProject
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.io.systemIndependentPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.file.Path

@RunWith(Parameterized::class)
class AutomaticModuleUnloaderTest(private val reloadingMode: ReloadingMode) {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()

    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun modes(): Array<ReloadingMode> = ReloadingMode.values()
  }

  @Rule
  @JvmField
  val tempDir = TempDirectory()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @Test
  fun `unload simple module`() = runBlocking {
    val project = createProject()
    createModule(project, "a")
    createModule(project, "b")
    withContext(Dispatchers.EDT) {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("a"))
    }
    createModule(project, "c")

    val moduleFiles = createNewModuleFiles(listOf("d")) {}
    val newProject = reloadProjectWithNewModules(project, moduleFiles)

    assertSameElements(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }, "a", "d")
  }

  @Test
  fun `unload modules with dependencies between them`() = runBlocking {
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
  fun `do not unload module if loaded module depends on it`() = runBlocking {
    val project = createProject()
    createModule(project, "a")
    val b = createModule(project, "b")
    ModuleRootModificationUtil.updateModel(b) {
      it.addInvalidModuleEntry("d")
    }
    doTest(project, "a", listOf("d"), {}, "a")
  }

  @Test
  fun `unload module if only unloaded module depends on it`() = runBlocking {
    val project = createProject()
    val a = createModule(project, "a")
    createModule(project, "b")
    ModuleRootModificationUtil.updateModel(a) {
      it.addInvalidModuleEntry("d")
    }
    doTest(project, "a", listOf("d"), {}, "a", "d")
  }

  @Test
  fun `do not unload modules if loaded module depends on them transitively`() = runBlocking {
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
  fun `unload module if loaded module transitively depends on it via previously unloaded module`() = runBlocking {
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
  fun `load unloaded module back before adding new module`() = runBlocking {
    val project = createProject()
    createModule(project, "root")
    createModule(project, "a")
    createModule(project, "b")
    withContext(Dispatchers.EDT) {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("a", "b"))
    }
    doTest(project, "b", listOf("c"), {}, "b", "c")
  }

  @Test
  fun `deleted iml file`() = runBlocking {
    val project = createProject()
    createModule(project, "a")
    createModule(project, "b")
    val deletedIml = createModule(project, "deleted")
    withContext(Dispatchers.EDT) {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("a"))
    }
    createModule(project, "c")

    val moduleFiles = createNewModuleFiles(listOf("d")) {}
    val deletedImlFile = File(deletedIml.moduleFilePath)
    val newProject = reloadProjectWithNewModules(project, moduleFiles) {
      deletedImlFile.delete()
    }

    assertSameElements(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }, "a", "d")
  }


  private suspend fun doTest(project: Project,
                     initiallyUnloaded: String,
                     newModulesName: List<String>,
                     setup: (Map<String, Module>) -> Unit,
                     vararg expectedUnloadedModules: String) {
    val moduleManager = ModuleManager.getInstance(project)
    withContext(Dispatchers.EDT) {
      moduleManager.setUnloadedModules(listOf(initiallyUnloaded))
    }

    val moduleFiles = createNewModuleFiles(newModulesName, setup)
    val newProject = reloadProjectWithNewModules(project, moduleFiles)

    assertSameElements(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }, *expectedUnloadedModules)
  }

  private suspend fun createProject(): Project {
    return ProjectManagerEx.getInstanceEx().openProjectAsync(tempDir.newDirectory("automaticReloaderTest").toPath(),
                                                             createTestOpenProjectOptions())!!
  }

  private suspend fun createModule(project: Project, moduleName: String): Module {
    return withContext(Dispatchers.EDT) {
      runWriteAction { ModuleManager.getInstance(project).newModule("${project.basePath}/$moduleName.iml", "JAVA") }
    }
  }

  private suspend fun createNewModuleFiles(moduleNames: List<String>, setup: (Map<String, Module>) -> Unit): List<Path> {
    val newModulesProjectDir = tempDir.newDirectory("newModules").toPath()
    val moduleFiles = moduleNames.map { newModulesProjectDir.resolve("$it.iml") }
    val project = ProjectManagerEx.getInstanceEx().newProjectAsync(newModulesProjectDir, createTestOpenProjectOptions())
    try {
      val moduleManager = ModuleManager.getInstance(project)
      withContext(Dispatchers.EDT) {
        ApplicationManager.getApplication().runWriteAction {
          moduleFiles.forEach {
            moduleManager.newModule(it.toAbsolutePath().toString(), StdModuleTypes.JAVA.id)
          }
        }
      }
      setup(moduleManager.modules.associateBy { it.name })
    }
    finally {
      saveAndCloseProject(project)
    }
    return moduleFiles
  }

  private suspend fun saveAndCloseProject(project: Project) {
    try {
      project.stateStore.save(forceSavingAllSettings = true)
    }
    finally {
      ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project)
    }
  }

  private suspend fun reloadProjectWithNewModules(project: Project, moduleFiles: List<Path>, beforeReload: () -> Unit = {}): Project {
    when (reloadingMode) {
      ReloadingMode.ON_THE_FLY -> PlatformTestUtil.saveProject(project, true)
      ReloadingMode.REOPEN -> saveAndCloseProject(project)
    }

    val modulesXmlFile = (project as ProjectStoreOwner).componentStore.directoryStorePath!!.resolve("modules.xml")
    val rootElement = JDOMUtil.load(modulesXmlFile)
    val moduleRootComponent = JDomSerializationUtil.findComponent(rootElement, JpsProjectLoader.MODULE_MANAGER_COMPONENT)
    val modulesTag = moduleRootComponent!!.getChild("modules")!!
    moduleFiles.forEach {
      val filePath = it.systemIndependentPath
      val fileUrl = VfsUtil.pathToUrl(filePath)
      modulesTag.addContent(Element("module").setAttribute("fileurl", fileUrl).setAttribute("filepath", filePath))
    }

    when (reloadingMode) {
      ReloadingMode.ON_THE_FLY -> {
        val modulesXmlCopyDir = tempDir.newDirectory("modules-xml").toPath()
        JDOMUtil.write(rootElement, modulesXmlCopyDir.resolve(".idea/modules.xml"))
        beforeReload()
        copyFilesAndReloadProject(project, modulesXmlCopyDir)
        disposableRule.register { PlatformTestUtil.forceCloseProjectWithoutSaving(project) }
        return project
      }
      ReloadingMode.REOPEN -> {
        JDOMUtil.write(rootElement, modulesXmlFile)
        beforeReload()
        return TestProjectManager.loadAndOpenProject(Path.of(project.basePath!!), disposableRule.disposable)
      }
    }
  }

  enum class ReloadingMode { ON_THE_FLY, REOPEN }
}
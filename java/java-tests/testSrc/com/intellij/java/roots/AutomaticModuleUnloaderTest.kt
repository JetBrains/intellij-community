// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.module.AutomaticModuleUnloader
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.ProjectStoreOwner
import com.intellij.project.TestProjectManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.configurationStore.copyFilesAndReloadProject
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.waitUntil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
@ParameterizedClass(name = "{0}")
@MethodSource("modes")
class AutomaticModuleUnloaderTest(private val reloadingMode: ReloadingMode) {
  companion object {
    @JvmStatic
    fun modes(): Array<ReloadingMode> = ReloadingMode.values()
  }

  @JvmField
  @RegisterExtension
  val tempDir = TempDirectoryExtension()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `unload simple module`(): Unit = runBlocking {
    val project = createProject()
    try {
      createModule(project, "a")
      createModule(project, "b")
      withContext(Dispatchers.EDT) {
        ModuleManager.getInstance(project).setUnloadedModules(listOf("a"))
      }
      createModule(project, "c")

      val moduleFiles = createNewModuleFiles(listOf("d")) {}

      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).containsExactlyInAnyOrder("b", "c")
        }.isSuccess
      }

      val newProject = reloadProjectWithNewModules(project, moduleFiles)

      waitUntil {
        runCatching {
          assertThat(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }).containsExactlyInAnyOrder("a", "d")
        }.isSuccess
      }
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `check loaded modules state`(): Unit = runBlocking {
    val project = createProject()
    try {
      createModule(project, "a")
      createModule(project, "b")
      withContext(Dispatchers.EDT) {
        ModuleManager.getInstance(project).setUnloadedModules(listOf("a"))
      }
      assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).containsExactlyInAnyOrder("b")
      createModule(project, "c")

      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).containsExactlyInAnyOrder("b", "c")
        }.isSuccess
      }

      createModule(project, "x")

      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).containsExactlyInAnyOrder("b", "c", "x")
        }.isSuccess
      }
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `loaded modules are empty if no modules are unloaded`(): Unit = timeoutRunBlocking {
    val project = createProject()
    try {
      assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).isEmpty()

      createModule(project, "a")
      createModule(project, "b")
      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).isEmpty()
        }.isSuccess
      }

      createModule(project, "c")

      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).isEmpty()
        }.isSuccess
      }
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `loaded modules list is updated when we set unloaded modules`(): Unit = timeoutRunBlocking {
    val project = createProject()
    try {
      createModule(project, "a")
      createModule(project, "b")
      createModule(project, "c")

      assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).isEmpty()

      withContext(Dispatchers.EDT) {
        ModuleManager.getInstance(project).setUnloadedModules(listOf("a"))
      }

      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).containsExactlyInAnyOrder("b", "c")
        }.isSuccess
      }

      withContext(Dispatchers.EDT) {
        ModuleManager.getInstance(project).setUnloadedModules(listOf("a", "b"))
      }

      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).containsExactlyInAnyOrder("c")
        }.isSuccess
      }

      withContext(Dispatchers.EDT) {
        ModuleManager.getInstance(project).setUnloadedModules(emptyList())
      }

      waitUntil {
        runCatching {
          assertThat(AutomaticModuleUnloader.getInstance(project).getLoadedModules()).isEmpty()
        }.isSuccess
      }
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `unload modules with dependencies between them`(): Unit = runBlocking {
    val project = createProject()
    try {
      createModule(project, "a")
      createModule(project, "b")
      doTest(project, "a", listOf("c", "d"), { modules ->
        ModuleRootModificationUtil.updateModel(modules.getValue("c")) {
          it.addModuleOrderEntry(modules.getValue("d"))
        }
      }, "a", "c", "d")
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `do not unload module if loaded module depends on it`(): Unit = runBlocking {
    val project = createProject()
    try {
      createModule(project, "a")
      val b = createModule(project, "b")
      ModuleRootModificationUtil.updateModel(b) {
        it.addInvalidModuleEntry("d")
      }
      doTest(project, "a", listOf("d"), {}, "a")
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `unload module if only unloaded module depends on it`(): Unit = runBlocking {
    val project = createProject()
    try {
      val a = createModule(project, "a")
      createModule(project, "b")
      ModuleRootModificationUtil.updateModel(a) {
        it.addInvalidModuleEntry("d")
      }
      doTest(project, "a", listOf("d"), {}, "a", "d")
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `do not unload modules if loaded module depends on them transitively`(): Unit = runBlocking {
    val project = createProject()
    try {
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
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `unload module if loaded module transitively depends on it via previously unloaded module`(): Unit = runBlocking {
    val project = createProject()
    try {
      val a = createModule(project, "a")
      val b = createModule(project, "b")
      ModuleRootModificationUtil.addDependency(a, b)
      ModuleRootModificationUtil.updateModel(b) {
        it.addInvalidModuleEntry("c")
      }
      doTest(project, "b", listOf("c"), {}, "b", "c")
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `load unloaded module back before adding new module`(): Unit = runBlocking {
    val project = createProject()
    try {
      createModule(project, "root")
      createModule(project, "a")
      createModule(project, "b")
      withContext(Dispatchers.EDT) {
        ModuleManager.getInstance(project).setUnloadedModules(listOf("a", "b"))
      }
      doTest(project, "b", listOf("c"), {}, "b", "c")
    }
    finally {
      project.closeProjectAsync()
    }
  }

  @Test
  fun `deleted iml file`(): Unit = runBlocking {
    val project = createProject()
    try {
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

      assertThat(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }).containsExactlyInAnyOrder("a", "d")
    }
    finally {
      project.closeProjectAsync()
    }
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

    assertThat(ModuleManager.getInstance(newProject).unloadedModuleDescriptions.map { it.name }).containsExactlyInAnyOrder(*expectedUnloadedModules)
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
            moduleManager.newModule(it.toAbsolutePath().toString(), JavaModuleType.getModuleType().id)
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
      ReloadingMode.ON_THE_FLY -> {
        withContext(Dispatchers.EDT) {
          ModuleRootModificationUtil.addContentRoot(ModuleManager.getInstance(project).modules.first(), project.basePath!!)
        }
        PlatformTestUtil.saveProject(project, true)
      }
      ReloadingMode.REOPEN -> saveAndCloseProject(project)
    }

    val modulesXmlFile = (project as ProjectStoreOwner).componentStore.directoryStorePath!!.resolve("modules.xml")
    val rootElement = JDOMUtil.load(modulesXmlFile)
    val moduleRootComponent = JDomSerializationUtil.findComponent(rootElement, JpsProjectLoader.MODULE_MANAGER_COMPONENT)
    val modulesTag = moduleRootComponent!!.getChild("modules")!!
    moduleFiles.forEach {
      val filePath = it.invariantSeparatorsPathString
      val fileUrl = VfsUtil.pathToUrl(filePath)
      modulesTag.addContent(Element("module").setAttribute("fileurl", fileUrl).setAttribute("filepath", filePath))
    }

    when (reloadingMode) {
      ReloadingMode.ON_THE_FLY -> {
        val modulesXmlCopyDir = tempDir.newDirectory("modules-xml").toPath()
        JDOMUtil.write(rootElement, modulesXmlCopyDir.resolve(".idea/modules.xml"))
        beforeReload()
        copyFilesAndReloadProject(project, modulesXmlCopyDir)
        Disposer.register(disposable) { PlatformTestUtil.forceCloseProjectWithoutSaving(project) }
        return project
      }
      ReloadingMode.REOPEN -> {
        JDOMUtil.write(rootElement, modulesXmlFile)
        beforeReload()
        return TestProjectManager.loadAndOpenProject(Path.of(project.basePath!!), disposable)
      }
    }
  }

  enum class ReloadingMode { ON_THE_FLY, REOPEN }
}
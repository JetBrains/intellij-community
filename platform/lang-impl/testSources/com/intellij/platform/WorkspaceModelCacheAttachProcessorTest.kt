// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.actions.ModuleDeleteProvider
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectEntitiesAttacher
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.testEntities.TestModuleEntitySource
import com.intellij.util.io.createDirectories
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name

internal class WorkspaceModelCacheAttachProcessorTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Test
  fun `attach without iml but with wsm cache`(): Unit = runBlocking {
    ProjectAttachProcessor.EP_NAME.point.registerExtension(WorkspaceModelCacheAttachProcessor(), disposableRule.disposable)
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    ProjectEntitiesAttacher.EP.point.registerExtension(object : ProjectEntitiesAttacher {
      override fun extractEntitiesToAttach(storage: EntityStorage): MutableEntityStorage {
        return MutableEntityStorage.create().also { newStorage ->
          newStorage.replaceBySource({ it is TestModuleEntitySource }, storage)
        }
      }
    }, disposableRule.disposable)
    val attachedProjectPath = tempDirManager.newPath("attached_project", refreshVfs = false).also { it.createDirectories() }
    val moduleName = "attached_project_module"
    ProjectManagerEx.getInstanceEx().openProjectAsync(attachedProjectPath)!!.useProjectAsync(true) { project ->
      WorkspaceModel.getInstance(project).update("Add a test module") {
        it.addEntity(ModuleEntity(moduleName, emptyList(), TestModuleEntitySource))
      }
    }
    assertThat(attachedProjectPath.resolve("attached_project_module.iml")).doesNotExist()

    val newProjectPath = tempDirManager.newPath("new_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(newProjectPath)!!.useProjectAsync { newProject ->
      assertThat(attachToProjectAsync(newProject, attachedProjectPath)).isTrue()
      val modules = newProject.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).toList()
      assertThat(modules).hasSize(1)
      assertThat(modules[0].name).isEqualTo(moduleName)
    }
  }

  @Test
  fun `test ProjectRootEntity migration`(): Unit = runBlocking {
    ProjectAttachProcessor.EP_NAME.point.registerExtension(WorkspaceModelCacheAttachProcessor(), disposableRule.disposable)
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    val attachedProjectPath = tempDirManager.newPath("attached_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(attachedProjectPath, OpenProjectTask { projectRootDir = attachedProjectPath })!!.useProjectAsync(true) { project ->
      assertThat(project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()).hasSize(1)
    }

    val newProjectPath = tempDirManager.newPath("new_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(newProjectPath, OpenProjectTask { projectRootDir = newProjectPath })!!.useProjectAsync { newProject ->
      assertThat(attachToProjectAsync(newProject, attachedProjectPath)).isTrue()
      assertThat(newProject.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()).hasSize(2)
    }
  }

  @Test
  fun `test primary module depends on attached module`(): Unit = runBlocking {
    registerAttachProcessors()
    val attachedProjectPath = createProjectWithModule("attached_project")

    val newProjectPath = tempDirManager.newPath("new_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(newProjectPath, OpenProjectTask { projectRootDir = newProjectPath })!!.useProjectAsync { newProject ->
      val primaryModule = addModule(newProject, newProjectPath)
      assertThat(attachToProjectAsync(newProject, attachedProjectPath)).isTrue()
      val attachedModule = ModuleManager.getInstance(newProject).findModuleByName(attachedProjectPath.name)!!
      assertThat(ModuleRootManager.getInstance(primaryModule).dependencies).containsExactly(attachedModule)
    }
  }

  @Test
  fun `test detach removes attached project root`(): Unit = runBlocking {
    registerAttachProcessors()
    val attachedProjectPath = createProjectWithModule("attached_project")

    val newProjectPath = tempDirManager.newPath("new_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(newProjectPath, OpenProjectTask { projectRootDir = newProjectPath })!!.useProjectAsync { newProject ->
      addModule(newProject, newProjectPath)
      assertThat(attachToProjectAsync(newProject, attachedProjectPath)).isTrue()
      assertThat(projectRoots(newProject)).hasSize(2)

      val attachedModule = ModuleManager.getInstance(newProject).findModuleByName(attachedProjectPath.name)!!
      writeAction {
        ModuleDeleteProvider.detachModules(newProject, arrayOf(attachedModule))
      }
      assertThat(projectRoots(newProject)).containsExactly(VfsUtilCore.pathToUrl(newProjectPath.invariantSeparatorsPathString))
    }
  }

  @Test
  fun `test do not duplicate global entities`(): Unit = runBlocking {
    ProjectAttachProcessor.EP_NAME.point.registerExtension(WorkspaceModelCacheAttachProcessor(), disposableRule.disposable)
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    val attachedProjectPath = tempDirManager.newPath("attached_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(attachedProjectPath)!!.useProjectAsync(true) { project ->
      val sdk = SimpleJavaSdkType().createJdk("_other", SystemProperties.getJavaHome())
      writeAction {
        ProjectJdkTable.getInstance(project).addJdk(sdk, disposableRule.disposable)
      }
    }

    val newProjectPath = tempDirManager.newPath("new_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(newProjectPath, OpenProjectTask { projectRootDir = newProjectPath })!!
      .useProjectAsync { newProject ->
        assertThat(attachToProjectAsync(newProject, attachedProjectPath)).isTrue()
        assertThat(newProject.workspaceModel.currentSnapshot.entities(SdkEntity::class.java).toList()).hasSize(1)
      }
  }

  private fun registerAttachProcessors() {
    ProjectAttachProcessor.EP_NAME.point.registerExtension(WorkspaceModelCacheAttachProcessor(), disposableRule.disposable)
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
  }

  /**
   * Creates a project with one `.iml` module that has the project directory as a content root,
   * then closes the project to save its workspace model cache.
   */
  private suspend fun createProjectWithModule(name: String): Path {
    val projectPath = tempDirManager.newPath(name, refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(projectPath, OpenProjectTask { projectRootDir = projectPath })!!.useProjectAsync(true) { project ->
      addModule(project, projectPath)
    }
    return projectPath
  }

  private suspend fun addModule(project: Project, projectPath: Path): Module {
    return writeAction {
      val module = ModuleManager.getInstance(project).newModule(projectPath.resolve(Project.DIRECTORY_STORE_FOLDER).resolve("${projectPath.name}.iml"), "")
      val model = ModuleRootManager.getInstance(module).modifiableModel
      model.addContentEntry(VfsUtilCore.pathToUrl(projectPath.invariantSeparatorsPathString))
      model.commit()
      module
    }
  }

  private fun projectRoots(project: Project): List<String> {
    return project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).map { it.root.url }.toList()
  }
}
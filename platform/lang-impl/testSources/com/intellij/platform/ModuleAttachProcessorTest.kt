// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.project.stateStore
import com.intellij.projectImport.ProjectEntitiesAttacher
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.testEntities.TestModuleEntitySource
import com.intellij.util.io.createDirectories
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.WEB_MODULE_ENTITY_TYPE_ID_NAME
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths
import kotlin.properties.Delegates

internal class ModuleAttachProcessorTest {
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
  fun `attach with iml`() = runBlocking {
    var existingProjectDir: String by Delegates.notNull()
    createOrLoadProject(tempDirManager) { existingProject ->
      existingProjectDir = existingProject.basePath!!
      backgroundWriteAction {
        ModuleManager.getInstance(existingProject).newModule("$existingProjectDir/test.iml", WEB_MODULE_ENTITY_TYPE_ID_NAME)
      }
      existingProject.stateStore.save()
    }

    createOrLoadProject(tempDirManager) { currentProject ->
      currentProject.stateStore.save()
      assertThat(ModuleAttachProcessor().attachToProjectAsync(currentProject, Paths.get(existingProjectDir), null)).isTrue()
    }
  }

  @Test
  fun `attach with iml and with wsm cache`() = runBlocking {
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)

    val existingProjectDir = tempDirManager.newPath("test", refreshVfs = false)
    ProjectManagerEx.getInstanceEx().openProjectAsync(existingProjectDir)!!.useProjectAsync(true) { existingProject ->
      backgroundWriteAction {
        ModuleManager.getInstance(existingProject).newModule("$existingProjectDir/test.iml", WEB_MODULE_ENTITY_TYPE_ID_NAME)
      }
      existingProject.stateStore.save()
    }

    createOrLoadProject(tempDirManager) { currentProject ->
      currentProject.stateStore.save()
      assertThat(ModuleAttachProcessor().attachToProjectAsync(currentProject, existingProjectDir, null)).isTrue()
      val modules = currentProject.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).toList()
      assertThat(modules).hasSize(1)
    }
  }

  @Test
  fun `attach without iml`() = runBlocking {
    createOrLoadProject(tempDirManager) { currentProject ->
      currentProject.stateStore.save()
      val existingProjectDir = tempDirManager.newPath().createDirectories()
      assertThat(ModuleAttachProcessor().attachToProjectAsync(currentProject, existingProjectDir, null)).isTrue()
    }
  }

  @Test
  fun `attach without iml but with wsm cache`(): Unit = runBlocking {
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
      assertThat(ModuleAttachProcessor().attachToProjectAsync(newProject, attachedProjectPath, null)).isTrue()
      val modules = newProject.workspaceModel.currentSnapshot.entities(ModuleEntity::class.java).toList()
      assertThat(modules).hasSize(1)
      assertThat(modules[0].name).isEqualTo(moduleName)
    }
  }

  @Test
  fun `test ProjectRootEntity migration`(): Unit = runBlocking {
    WorkspaceModelCacheImpl.forceEnableCaching(disposableRule.disposable)
    val attachedProjectPath = tempDirManager.newPath("attached_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(attachedProjectPath, OpenProjectTask { projectRootDir = attachedProjectPath })!!.useProjectAsync(true) { project ->
      assertThat(project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()).hasSize(1)
    }

    val newProjectPath = tempDirManager.newPath("new_project", refreshVfs = false).also { it.createDirectories() }
    ProjectManagerEx.getInstanceEx().openProjectAsync(newProjectPath, OpenProjectTask { projectRootDir = newProjectPath })!!.useProjectAsync { newProject ->
      assertThat(ModuleAttachProcessor().attachToProjectAsync(newProject, attachedProjectPath, null)).isTrue()
      assertThat(newProject.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()).hasSize(2)
    }
  }

  @Test
  fun `test do not duplicate global entities`(): Unit = runBlocking {
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
        assertThat(ModuleAttachProcessor().attachToProjectAsync(newProject, attachedProjectPath, null)).isTrue()
        assertThat(newProject.workspaceModel.currentSnapshot.entities(SdkEntity::class.java).toList()).hasSize(1)
      }
  }
}
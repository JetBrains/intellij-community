// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createOrLoadProject
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.io.createDirectories
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
      assertThat(ModuleAttachProcessor().isEnabled(currentProject, existingProjectDir, null)).isFalse()
    }
  }
}
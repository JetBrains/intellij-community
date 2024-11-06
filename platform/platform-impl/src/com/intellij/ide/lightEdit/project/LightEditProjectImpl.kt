// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.lightEdit.LightEditUtil.PROJECT_NAME
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectServiceInitializer
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.platform.project.ProjectEntitiesStorage
import com.intellij.serviceContainer.ComponentManagerImpl
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Path

private val projectPath: Path
  get() = Path.of(PathManager.getConfigPath() + File.separator + "light-edit")

internal class LightEditProjectImpl private constructor(projectPath: Path) :
  ProjectImpl(parent = ApplicationManager.getApplication() as ComponentManagerImpl,
              filePath = projectPath,
              projectName = PROJECT_NAME), LightEditCompatible {
  constructor() : this(projectPath)

  init {
    registerComponents()
    customizeRegisteredComponents()
    componentStore.setPath(projectPath, false, null)
    runUnderModalProgressIfIsEdt {
      val project = this@LightEditProjectImpl
      ProjectServiceInitializer.initEssential(project)
      ProjectEntitiesStorage.getInstance().createEntity(project)
      schedulePreloadServices(project)
      launch {
        project.createComponentsNonBlocking()
      }
      ProjectServiceInitializer.initNonEssential(project)
    }
  }

  private fun customizeRegisteredComponents() {
    val pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
    if (pluginDescriptor == null) {
      logger<LightEditProjectImpl>().error("Could not find plugin by id: ${PluginManagerCore.CORE_ID}")
      return
    }

    registerService(serviceInterface = DirectoryIndex::class.java,
                    implementation = LightEditDirectoryIndex::class.java,
                    pluginDescriptor = pluginDescriptor,
                    override = true)
    registerService(serviceInterface = ProjectFileIndex::class.java,
                    implementation = LightEditProjectFileIndex::class.java,
                    pluginDescriptor = pluginDescriptor,
                    override = true)
    registerService(serviceInterface = FileIndexFacade::class.java,
                    implementation = LightEditFileIndexFacade::class.java,
                    pluginDescriptor = pluginDescriptor,
                    override = true)
    registerService(serviceInterface = DumbService::class.java,
                    implementation = LightEditDumbService::class.java,
                    pluginDescriptor = pluginDescriptor,
                    override = true)
    registerService(serviceInterface = FileEditorManager::class.java,
                    implementation = LightEditFileEditorManagerImpl::class.java,
                    pluginDescriptor = pluginDescriptor,
                    override = true)
  }

  override fun setProjectName(value: String) {
    throw IllegalStateException()
  }

  override fun getName(): String = PROJECT_NAME

  override fun getLocationHash(): String = name

  override fun isOpen(): Boolean = true

  override fun isInitialized(): Boolean = true
}
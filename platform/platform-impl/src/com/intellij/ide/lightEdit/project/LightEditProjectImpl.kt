// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.projectInitListeners
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.DirectoryIndex
import java.io.File
import java.nio.file.Path

internal class LightEditProjectImpl private constructor(projectPath: Path) : ProjectImpl(projectPath, NAME), LightEditCompatible {
  companion object {
    private val LOG = logger<LightEditProjectImpl>()
    private const val NAME = "LightEditProject"

    private val projectPath: Path
      get() = Path.of(PathManager.getConfigPath() + File.separator + "light-edit")
  }

  constructor() : this(projectPath)

  init {
    registerComponents()
    customizeRegisteredComponents()
    componentStore.setPath(projectPath, false, null)
    runUnderModalProgressIfIsEdt {
      preloadServicesAndCreateComponents(project = this@LightEditProjectImpl, preloadServices = true)
      projectInitListeners {
        it.execute(this@LightEditProjectImpl)
      }
    }
  }

  private fun customizeRegisteredComponents() {
    val pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
    if (pluginDescriptor == null) {
      LOG.error("Could not find plugin by id: ${PluginManagerCore.CORE_ID}")
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

  override fun getName() = NAME

  override fun getLocationHash() = name

  override fun isOpen() = true

  override fun isInitialized() = true
}
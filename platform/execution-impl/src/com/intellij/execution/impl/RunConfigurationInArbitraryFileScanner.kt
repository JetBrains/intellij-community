// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.ProjectConfigurationFileScanner
import com.intellij.util.indexing.roots.loadProjectConfigurationFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * This class doesn't push any file properties, it is used for scanning the project for `*.run.xml` files - files with run configurations.
 * This is to handle run configurations stored in arbitrary files within project content (not in .idea/runConfigurations or project.ipr file).
 */
internal class RunConfigurationInArbitraryFileScanner : ProjectConfigurationFileScanner(".run.xml") {
  override fun createFileHandler(project: Project): (VirtualFile) -> Unit {
    if (!isRunConfigsFromArbitraryFilesEnabled()) {
      return {}
    }
    val runManager by lazy(LazyThreadSafetyMode.NONE) { RunManagerImpl.getInstanceImpl(project) }
    return { file ->
      project.service<ScopeService>().scope.launch {
        readAction {
          runManager.updateRunConfigsFromArbitraryFiles(emptyList(), listOf(file.path))
        }
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class ScopeService(val scope: CoroutineScope)

internal fun loadFileWithRunConfigs(project: Project): List<String> =
  if (!isRunConfigsFromArbitraryFilesEnabled()) listOf() else loadProjectConfigurationFiles(project, ".run.xml")

internal fun isRunConfigsFromArbitraryFilesEnabled(): Boolean =
  RegistryManager.getInstance().`is`("run.configurations.from.arbitrary.files")

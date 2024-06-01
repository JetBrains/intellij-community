// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modifyModules
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DefaultSubprojectHandler: SubprojectHandler {
  override fun getSubprojects(workspace: Project): List<Subproject> {
    return getWorkspaceSettings(workspace).subprojects.mapNotNull {
      val name = it.name
      val path = it.path
      if (name != null && path != null ) DefaultSubproject(this, name, path) else null
    }
  }

  override fun canImportFromFile(file: VirtualFile) = file.isDirectory

  override fun removeSubprojects(workspace: Project, subprojects: List<Subproject>) {
    subprojects.forEach { subproject ->
      getWorkspaceSettings(workspace).subprojects.removeIf { it.path == subproject.projectPath }
      workspace.modifyModules {
        modules.filter { it.moduleFilePath.startsWith(subproject.projectPath) }
          .forEach(::disposeModule)
      }
    }
  }

  override fun importFromProject(project: Project): ImportedProjectSettings = DefaultImportedSettings(project)

  override val subprojectIcon = ExpUiIcons.Nodes.Folder
}

private class DefaultSubproject(override val handler: SubprojectHandler,
                                override val name: String,
                                override val projectPath: String) : Subproject

private class DefaultImportedSettings(project: Project): ImportedProjectSettings {
  private val projectDir = requireNotNull(project.basePath)
  private val projectName = project.name
  private val moduleImlPaths: MutableSet<String>
  private val hasInheritedOutputPath: Boolean

  init {
    val modules = ModuleManager.getInstance(project).modules
    moduleImlPaths = modules.filter { ModuleType.get(it).id != "JAVA_MODULE" }.map { it.moduleFilePath }.toMutableSet()
    hasInheritedOutputPath = modules.any { CompilerModuleExtension.getInstance(it)?.isCompilerOutputPathInherited == true }
  }

  override suspend fun applyTo(workspace: Project): Boolean {
    if (moduleImlPaths.isEmpty()) return false
    withContext(Dispatchers.EDT) {
      workspace.modifyModules {
        moduleImlPaths.removeAll(modules.map { it.moduleFilePath }.toSet())
        for (moduleImlPath in moduleImlPaths) {
          loadModule(moduleImlPath)
        }
      }
      if (hasInheritedOutputPath) { // todo remove after introducing proper inheritance
        val outputUrl = VfsUtilCore.pathToUrl(requireNotNull(workspace.basePath)) + "/out"
        CompilerProjectExtension.getInstance(workspace)?.compilerOutputUrl = outputUrl
      }
    }
    getWorkspaceSettings(workspace).subprojects.add(SubprojectSettings(projectName, projectDir))
    return true
  }
}
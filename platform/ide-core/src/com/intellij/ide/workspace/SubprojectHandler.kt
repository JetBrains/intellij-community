// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
interface SubprojectHandler {
  companion object {
    val EP_NAME: ExtensionPointName<SubprojectHandler> = ExtensionPointName.create("com.intellij.workspace.subprojectHandler")
    fun getAllSubprojects(project: Project): List<Subproject> = EP_NAME.extensionList.flatMap { it.getSubprojects(project) }
  }

  fun getSubprojects(project: Project): List<Subproject>
  fun canImportFromFile(project: Project, file: VirtualFile): Boolean
  suspend fun importFromFile(project: Project, file: VirtualFile)
  fun importFromProject(project: Project, newWorkspace: Boolean): ImportedProjectSettings?

  fun suppressGenericImportFor(module: Module): Boolean = false
}

interface WorkspaceSettingsImporter {
  companion object {
    val EP_NAME: ExtensionPointName<WorkspaceSettingsImporter> = ExtensionPointName.create("com.intellij.workspace.settingsImporter")
  }

  fun importFromProject(project: Project, newWorkspace: Boolean): ImportedProjectSettings?
}

interface ImportedProjectSettings {
  suspend fun applyTo(workspace: Project)
}

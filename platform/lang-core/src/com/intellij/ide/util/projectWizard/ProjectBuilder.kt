// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard

import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

abstract class ProjectBuilder {
  open val isUpdate: Boolean
    get() = false

  abstract fun commit(project: Project, model: ModifiableModuleModel?, modulesProvider: ModulesProvider?): List<Module>

  open fun commit(project: Project): List<Module> {
    return commit(project, null)
  }

  open fun commit(project: Project, model: ModifiableModuleModel?): List<Module> {
    return commit(project, model, DefaultModulesProvider.createForProject(project))
  }

  open fun validate(currentProject: Project?, project: Project): Boolean = true

  open fun cleanup() {
  }

  open val isOpenProjectSettingsAfter: Boolean
    get() = false

  open fun isSuitableSdkType(sdkType: SdkTypeId): Boolean = true

  open fun createProject(name: String, path: String): Project? {
    return ProjectManager.getInstance().createProject(name, path)
  }

  /**
   * Configure project when it's added to workspace as module.
   */
  @ApiStatus.Internal
  open fun createProjectConfigurator(): ProjectConfigurator? = null
}

@ApiStatus.Internal
interface ProjectConfigurator {
  fun configureProject(workspace: Project, projectDir: VirtualFile)
}
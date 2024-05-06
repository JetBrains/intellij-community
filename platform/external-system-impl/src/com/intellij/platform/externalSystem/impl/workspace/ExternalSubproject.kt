// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.workspace

import com.intellij.ide.workspace.Subproject
import com.intellij.openapi.externalSystem.action.DetachExternalProjectAction
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
class ExternalSubproject(override val workspace: Project, val projectInfo: ExternalProjectInfo) : Subproject {

  override val name: String
    get() = projectInfo.externalProjectStructure?.data?.externalName
            ?: FileUtil.getNameWithoutExtension(projectPath)
  override val projectPath: String get() = projectInfo.externalProjectPath

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ExternalSubproject

    if (workspace != other.workspace) return false
    if (projectInfo != other.projectInfo) return false

    return true
  }

  override fun hashCode(): Int {
    var result = workspace.hashCode()
    result = 31 * result + projectInfo.hashCode()
    return result
  }

  override fun removeSubproject() {
    val info = requireNotNull(projectInfo)
    val data = requireNotNull(info.externalProjectStructure?.data)
    DetachExternalProjectAction.detachProject(workspace, info.projectSystemId, data, null)
  }
}
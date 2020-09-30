// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to show and hide notification about unlinked projects with [systemId].
 */
@ApiStatus.Experimental
interface ExternalSystemUnlinkedProjectAware {

  val systemId: ProjectSystemId

  fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean

  fun isLinkedProject(project: Project, externalProjectPath: String): Boolean

  fun linkAndLoadProject(project: Project, externalProjectPath: String)

  fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable)

  companion object {
    val EP_NAME = ExtensionPointName.create<ExternalSystemUnlinkedProjectAware>("com.intellij.externalSystemUnlinkedProjectAware")
  }
}
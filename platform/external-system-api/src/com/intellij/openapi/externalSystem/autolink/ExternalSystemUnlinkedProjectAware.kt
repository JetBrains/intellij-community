// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Allows showing and hiding notification about unlinked projects with [systemId].
 */
interface ExternalSystemUnlinkedProjectAware {
  val systemId: ProjectSystemId

  fun buildFileExtensions(): Array<String> = arrayOf()

  fun isBuildFile(project: Project, buildFile: VirtualFile): Boolean

  fun isLinkedProject(project: Project, externalProjectPath: String): Boolean

  @Deprecated("use async method instead")
  fun linkAndLoadProject(project: Project, externalProjectPath: String) {
    throw UnsupportedOperationException()
  }

  suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String) {
    withContext(Dispatchers.EDT) {
      blockingContext {
        @Suppress("DEPRECATION")
        linkAndLoadProject(project, externalProjectPath)
      }
    }
  }

  suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    throw UnsupportedOperationException("'unlinkProject' method in ${this::class.qualifiedName} is not implemented")
  }

  fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable)

  companion object {
    val EP_NAME: ExtensionPointName<ExternalSystemUnlinkedProjectAware> =
      ExtensionPointName.create<ExternalSystemUnlinkedProjectAware>("com.intellij.externalSystemUnlinkedProjectAware")

    @JvmStatic
    fun getInstance(systemId: ProjectSystemId): ExternalSystemUnlinkedProjectAware? =
      EP_NAME.findFirstSafe { it.systemId == systemId }

    @JvmStatic
    @Internal
    suspend fun unlinkOtherLinkedProjects(project: Project, externalProjectPath: String, systemId: ProjectSystemId) {
      EP_NAME.forEachExtensionSafeAsync { extension ->
        if (extension.systemId != systemId && extension.isLinkedProject(project, externalProjectPath)) {
          LOG.info("Unlinking $systemId project ${externalProjectPath}")
          extension.unlinkProject(project, externalProjectPath)
        }
      }
    }

    private val LOG = logger<ExternalSystemUnlinkedProjectAware>()
  }
}

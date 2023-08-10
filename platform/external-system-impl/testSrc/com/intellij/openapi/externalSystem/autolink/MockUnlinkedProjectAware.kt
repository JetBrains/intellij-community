// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.DisposableWrapperList
import java.util.concurrent.atomic.AtomicInteger

class MockUnlinkedProjectAware(
  override val systemId: ProjectSystemId,
  private val buildFileExtension: String
) : ExternalSystemUnlinkedProjectAware {
  private val linkedProjects = HashSet<String>()
  private val listeners = DisposableWrapperList<(String) -> Unit>()

  val linkCounter = AtomicInteger()

  fun getProjectId(projectDirectory: VirtualFile): ExternalSystemProjectId {
    return ExternalSystemProjectId(systemId, projectDirectory.path)
  }

  override fun isBuildFile(project: Project, buildFile: VirtualFile) = isBuildFile(buildFile)
  fun isBuildFile(buildFile: VirtualFile): Boolean {
    return buildFile.extension == buildFileExtension
  }

  override fun isLinkedProject(project: Project, externalProjectPath: String): Boolean {
    return externalProjectPath in linkedProjects
  }

  override fun linkAndLoadProject(project: Project, externalProjectPath: String) = linkProject(externalProjectPath)
  fun linkProject(externalProjectPath: String) {
    linkCounter.incrementAndGet()
    linkedProjects.add(externalProjectPath)
    listeners.forEach { it(externalProjectPath) }
  }

  override fun subscribe(project: Project, listener: ExternalSystemProjectLinkListener, parentDisposable: Disposable) {
    listeners.add(listener::onProjectLinked, parentDisposable)
  }
}
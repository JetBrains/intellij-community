package com.intellij.ide.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.entities
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.ProjectRootEntitySource

internal fun addRemoveProjectRootEntities(project: Project, roots: List<VirtualFile>, add: Boolean) {
  val workspaceModel = project.workspaceModel
  val urls = roots.map { it.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager()) }.toSet()
  workspaceModel.updateProjectModel("${if (add) "Adding" else "Removing"} attached dirs: $urls") { storage ->
    if (add) {
      val addUrls = urls.minus(storage.entities<ProjectRootEntity>().map { it.root }.toSet())
      addUrls.forEach { root -> storage.addEntity(ProjectRootEntity(root, ProjectRootEntitySource)) }
    }
    else {
      storage.entities<ProjectRootEntity>().filter { it.root in urls }.forEach { entity -> storage.removeEntity(entity) }
    }
  }
}

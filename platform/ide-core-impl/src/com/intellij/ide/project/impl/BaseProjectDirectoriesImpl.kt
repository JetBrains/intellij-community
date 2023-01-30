// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.project.impl

import com.intellij.openapi.project.BaseProjectDirectories
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePrefixTreeFactory
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity

class BaseProjectDirectoriesImpl(project: Project) : BaseProjectDirectories() {
  private val contentRoots = VirtualFilePrefixTreeFactory.createSet(
    WorkspaceModel.getInstance(project).currentSnapshot.entities(ContentRootEntity::class.java).mapNotNull { contentRootEntity ->
      contentRootEntity.url.virtualFile?.takeIf { it.isDirectory } 
    }
  )
  
  init {
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        event.getChanges(ContentRootEntity::class.java).forEach { change ->
          change.oldEntity?.url?.virtualFile?.let { contentRoots.remove(it) }
          change.newEntity?.url?.virtualFile?.let { contentRoots.add(it) }
        }                                                              
      }
    })
  }

  override fun getBaseDirectories(): Sequence<VirtualFile> {
    return contentRoots.getRootSequence()
  }

  override fun getBaseDirectoryFor(virtualFile: VirtualFile): VirtualFile? {
    return contentRoots.getAncestors(virtualFile).firstOrNull()
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.packaging.artifacts.Artifact
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactBridge
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.packaging.impl.elements.FileOrDirectoryCopyPackagingElement
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.WorkspaceModel.Companion.getInstance
import com.intellij.workspaceModel.storage.CachedValue
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.ExternalEntityMapping
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.FileOrDirectoryPackagingElementEntity

internal class ArtifactVirtualFileListener(private val project: Project) : BulkFileListener {
  private val parentPathsToArtifacts: CachedValue<Map<String, List<ArtifactEntity>>> = CachedValue { storage: EntityStorage ->
    computeParentPathToArtifactMap(storage)
  }

  override fun after(events: List<VFileEvent>) {
    for (event in events) {
      if (event is VFileMoveEvent) {
        filePathChanged(event.oldPath, event.getPath())
      }
      else if (event is VFilePropertyChangeEvent) {
        propertyChanged(event)
      }
    }
  }

  private fun filePathChanged(oldPath: String, newPath: String) {
    val artifactEntities = parentPathToArtifacts[oldPath] ?: return
    val artifactManager = ArtifactManager.getInstance(project)

    //this is needed to set up mapping from ArtifactEntity to ArtifactBridge
    artifactManager.artifacts

    val artifactsMap: ExternalEntityMapping<ArtifactBridge> = getInstance(project).currentSnapshot.artifactsMap
    val model = artifactManager.createModifiableModel()
    for (artifactEntity in artifactEntities) {
      val artifact = artifactsMap.getDataByEntity(artifactEntity) ?: continue
      val copy: Artifact = model.getOrCreateModifiableArtifact(artifact)
      ArtifactUtil.processFileOrDirectoryCopyElements(copy, object : PackagingElementProcessor<FileOrDirectoryCopyPackagingElement<*>?>() {
        override fun process(element: FileOrDirectoryCopyPackagingElement<*>, pathToElement: PackagingElementPath): Boolean {
          val path = element.filePath
          if (FileUtil.startsWith(path, oldPath)) {
            element.filePath = newPath + path.substring(oldPath.length)
          }
          return true
        }
      }, artifactManager.resolvingContext, false)
    }
    model.commit()
  }

  private val parentPathToArtifacts: Map<String, List<ArtifactEntity>>
    get() = getInstance(project).entityStorage.cachedValue(parentPathsToArtifacts)

  private fun propertyChanged(event: VFilePropertyChangeEvent) {
    if (VirtualFile.PROP_NAME == event.propertyName) {
      val parent = event.file.parent
      if (parent != null) {
        val parentPath = parent.path
        filePathChanged(parentPath + "/" + event.oldValue, parentPath + "/" + event.newValue)
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ArtifactVirtualFileListener::class.java)

    private fun computeParentPathToArtifactMap(storage: EntityStorage): Map<String, MutableList<ArtifactEntity>> {
      val result: MutableMap<String, MutableList<ArtifactEntity>> = HashMap()
      storage.entities(ArtifactEntity::class.java).forEach { artifact ->
        processFileOrDirectoryCopyElements(artifact) { entity: FileOrDirectoryPackagingElementEntity ->
          var path = VfsUtilCore.urlToPath(entity.filePath.url)
          while (path.isNotEmpty()) {
            result.getOrPut(path) { ArrayList() }.add(artifact)
            path = PathUtil.getParentPath(path)
          }
          true
        }
      }
      return result
    }
  }
}

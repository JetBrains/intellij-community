// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts

import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.FileOrDirectoryPackagingElementEntity
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
import com.intellij.platform.backend.workspace.WorkspaceModel.Companion.getInstance
import com.intellij.platform.backend.workspace.useQueryCacheWorkspaceModelApi
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.Compiler
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.query.flatMap
import com.intellij.platform.workspace.storage.query.groupBy
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

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

  private fun filePathChanged(oldPath: String, newPath: String) = filePathChangedMs.addMeasuredTimeMillis {
    val artifactEntities = if (useQueryCacheWorkspaceModelApi()) {
      val refs = parentPathToArtifactReferences[oldPath]?.asSequence() ?: return@addMeasuredTimeMillis
      val storage = project.workspaceModel.currentSnapshot
      refs.map { it.resolve(storage)!! }
    }
    else {
      parentPathToArtifacts[oldPath]?.asSequence() ?: return@addMeasuredTimeMillis
    }
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

  private val parentPathToArtifactReferences: Map<String, List<EntityPointer<ArtifactEntity>>>
    get() {
      val storage = project.workspaceModel.currentSnapshot
      return (storage as ImmutableEntityStorage).cached(query)
    }

  private val parentPathToArtifacts: Map<String, List<ArtifactEntity>>
    get() = (getInstance(project) as WorkspaceModelImpl).entityStorage.cachedValue(parentPathsToArtifacts)

  private fun propertyChanged(event: VFilePropertyChangeEvent) = propertyChangedMs.addMeasuredTimeMillis {
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

    private val query = entities<ArtifactEntity>()
      .flatMap { artifactEntity, _ ->
        buildList {
          processFileOrDirectoryCopyElements(artifactEntity) { entity ->
            var path = VfsUtilCore.urlToPath(entity.filePath.url)
            while (path.isNotEmpty()) {
              add(artifactEntity.createPointer<ArtifactEntity>() to path)
              path = PathUtil.getParentPath(path)
            }
            true
          }
        }
      }
      .groupBy({ it.second }, { it.first })

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

    private val filePathChangedMs: AtomicLong = AtomicLong()
    private val propertyChangedMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val filePathChangedGauge = meter.gaugeBuilder("compiler.ArtifactVirtualFileListener.filePathChanged.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val propertyChangedGauge = meter.gaugeBuilder("compiler.ArtifactVirtualFileListener.propertyChanged.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          filePathChangedGauge.record(filePathChangedMs.get())
          propertyChangedGauge.record(propertyChangedMs.get())
        },
        filePathChangedGauge, propertyChangedGauge,
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(Compiler))
    }
  }
}

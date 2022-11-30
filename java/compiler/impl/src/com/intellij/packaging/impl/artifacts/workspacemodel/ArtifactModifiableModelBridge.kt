// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.impl.artifacts.ArtifactPointerManagerImpl
import com.intellij.packaging.impl.artifacts.ArtifactUtil
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.VALID_ARTIFACT_CONDITION
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.mutableArtifactsMap
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.mapInPlace
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.JpsEntitySourceFactory
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.addArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactId
import com.intellij.workspaceModel.storage.bridgeEntities.CompositePackagingElementEntity
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

class ArtifactModifiableModelBridge(
  private val project: Project,
  internal val diff: MutableEntityStorage,
  private val manager: ArtifactManagerBridge,
) : ModifiableArtifactModel {

  internal val modifiableToOriginal = BidirectionalMap<ArtifactBridge, ArtifactBridge>()
  private val eventDispatcher = EventDispatcher.create(ArtifactListener::class.java)

  internal val elementsWithDiff = mutableSetOf<PackagingElement<*>>()

  private val versionedOnBuilder = VersionedEntityStorageOnBuilder(diff)

  override fun getArtifacts(): Array<ArtifactBridge> {
    val newBridges = mutableListOf<ArtifactBridge>()
    val artifacts = diff
      .entities(ArtifactEntity::class.java)
      .map { artifactEntity ->
        diff.artifactsMap.getDataByEntity(artifactEntity) ?: createArtifactBridge(artifactEntity, versionedOnBuilder, project).also {
          newBridges.add(it)
          manager.artifactWithDiffs.add(it)
        }
      }
      .filter { VALID_ARTIFACT_CONDITION.value(it) }
      .toList().toTypedArray()
    addBridgesToDiff(newBridges, diff)
    return artifacts.mapInPlace { modifiableToOriginal.getKeysByValue(it)?.singleOrNull() ?: it }
  }

  override fun findArtifact(name: String): Artifact? {
    val artifactEntity = diff.resolve(ArtifactId(name)) ?: return null

    val newBridges = mutableListOf<ArtifactBridge>()
    val bridge = diff.artifactsMap.getDataByEntity(artifactEntity)
                 ?: createArtifactBridge(artifactEntity, versionedOnBuilder, project).also {
                   newBridges.add(it)
                   manager.artifactWithDiffs.add(it)
                 }
    addBridgesToDiff(newBridges, diff)

    return modifiableToOriginal.getKeysByValue(bridge)?.singleOrNull() ?: bridge
  }

  override fun getArtifactByOriginal(artifact: Artifact): Artifact {
    return modifiableToOriginal.getKeysByValue(artifact as ArtifactBridge)?.singleOrNull() ?: artifact
  }

  override fun getOriginalArtifact(artifact: Artifact): Artifact {
    return modifiableToOriginal[artifact as ArtifactBridge] ?: artifact
  }

  override fun getArtifactsByType(type: ArtifactType): Collection<Artifact> {
    val typeId = type.id

    val newBridges = mutableListOf<ArtifactBridge>()
    val artifacts = diff
      .entities(ArtifactEntity::class.java)
      .filter { it.artifactType == typeId }
      .map { artifactEntity ->
        diff.artifactsMap.getDataByEntity(artifactEntity) ?: createArtifactBridge(artifactEntity, versionedOnBuilder, project).also {
          newBridges.add(it)
          manager.artifactWithDiffs.add(it)
        }
      }
      .toList()
    addBridgesToDiff(newBridges, diff)
    return artifacts
  }

  override fun getAllArtifactsIncludingInvalid(): List<Artifact> {
    val newBridges = mutableListOf<ArtifactBridge>()
    val artifacts = diff
      .entities(ArtifactEntity::class.java)
      .map { artifactEntity ->
        diff.artifactsMap.getDataByEntity(artifactEntity) ?: createArtifactBridge(artifactEntity, versionedOnBuilder, project).also {
          newBridges.add(it)
          manager.artifactWithDiffs.add(it)
        }
      }
      .toMutableList()
    addBridgesToDiff(newBridges, diff)
    return artifacts
  }

  override fun addArtifact(name: String, artifactType: ArtifactType): ModifiableArtifact {
    return addArtifact(name, artifactType, artifactType.createRootElement(name))
  }

  override fun addArtifact(name: String, artifactType: ArtifactType, rootElement: CompositePackagingElement<*>): ModifiableArtifact {
    return addArtifact(name, artifactType, rootElement, null)
  }

  override fun addArtifact(name: String,
                           artifactType: ArtifactType,
                           rootElement: CompositePackagingElement<*>,
                           externalSource: ProjectModelExternalSource?): ModifiableArtifact {
    val uniqueName = generateUniqueName(name)

    val outputPath = ArtifactUtil.getDefaultArtifactOutputPath(uniqueName, project)

    val fileManager = VirtualFileUrlManager.getInstance(project)

    val source = JpsEntitySourceFactory.createEntitySourceForArtifact(project, externalSource)

    val rootElementEntity = rootElement.getOrAddEntity(diff, source, project) as CompositePackagingElementEntity
    rootElement.forThisAndFullTree {
      if (!it.hasStorage()) {
        it.setStorage(versionedOnBuilder, project, elementsWithDiff, PackagingElementInitializer)
        elementsWithDiff += it
      }
    }

    val outputUrl = outputPath?.let { fileManager.fromPath(it) }
    val artifactEntity = diff.addArtifactEntity(
      uniqueName, artifactType.id, false,
      outputUrl, rootElementEntity, source
    )

    val symbolicId = artifactEntity.symbolicId
    val modifiableArtifact = ArtifactBridge(symbolicId, versionedOnBuilder, project, eventDispatcher, null)
    modifiableToOriginal[modifiableArtifact] = modifiableArtifact
    diff.mutableArtifactsMap.addMapping(artifactEntity, modifiableArtifact)

    eventDispatcher.multicaster.artifactAdded(modifiableArtifact)

    return modifiableArtifact
  }

  private fun generateUniqueName(baseName: String): String {
    return UniqueNameGenerator.generateUniqueName(baseName) { findArtifact(it) == null }
  }

  override fun removeArtifact(artifact: Artifact) {
    artifact as ArtifactBridge
    val original = modifiableToOriginal[artifact]
    if (original != null) {
      modifiableToOriginal.remove(artifact)

      diff.removeEntity(diff.get(ArtifactId(artifact.name)))
      eventDispatcher.multicaster.artifactRemoved(original)
    }
    else {
      modifiableToOriginal.removeValue(artifact)
      val entities = diff.artifactsMap.getEntities(artifact)
      entities.forEach { diff.removeEntity(it) }
      eventDispatcher.multicaster.artifactRemoved(artifact)
    }
  }

  override fun getOrCreateModifiableArtifact(artifact: Artifact): ModifiableArtifact {
    if (artifact as ArtifactBridge in modifiableToOriginal) return artifact

    val entity = diff.artifactsMap.getEntities(artifact).singleOrNull() as? ArtifactEntity ?: error("Artifact doesn't exist")
    val artifactId = entity.symbolicId
    val existingModifiableArtifact = modifiableToOriginal.getKeysByValue(artifact)?.singleOrNull()
    if (existingModifiableArtifact != null) return existingModifiableArtifact

    val modifiableArtifact = ArtifactBridge(artifactId, versionedOnBuilder, project, eventDispatcher, artifact)
    modifiableToOriginal[modifiableArtifact] = artifact
    eventDispatcher.multicaster.artifactChanged(modifiableArtifact, artifact.name)
    return modifiableArtifact
  }

  override fun getModifiableCopy(artifact: Artifact): Artifact? {
    return modifiableToOriginal.getKeysByValue(artifact as ArtifactBridge)?.singleOrNull()
  }

  override fun addListener(listener: ArtifactListener) {
    eventDispatcher.addListener(listener)
  }

  override fun removeListener(listener: ArtifactListener) {
    eventDispatcher.removeListener(listener)
  }

  override fun isModified(): Boolean {
    // TODO: 03.02.2021 May give a wrong result
    return diff.hasChanges()
  }

  @RequiresWriteLock
  override fun commit() {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    manager.commit(this)
  }

  override fun dispose() {
    val artifacts: MutableList<Artifact> = ArrayList()

    val modifiableToOriginalCopy = BidirectionalMap<ArtifactBridge, ArtifactBridge>()
    modifiableToOriginal.forEach { (mod, orig) -> modifiableToOriginalCopy[mod] = orig }

    for (artifact in modifiableToOriginalCopy.keys) {
      if (modifiableToOriginalCopy[artifact] == artifact) {
        artifacts.add(artifact)
      }
    }
    (ArtifactPointerManager.getInstance(project) as ArtifactPointerManagerImpl).disposePointers(artifacts)

    val current = WorkspaceModel.getInstance(project).entityStorage.current
    val changes = diff.collectChanges(current)[ArtifactEntity::class.java] ?: emptyList()

    val added = mutableListOf<ArtifactBridge>()
    val changed = mutableListOf<ArtifactBridge>()

    changes.forEach {
      when (it) {
        is EntityChange.Removed<*> -> Unit
        is EntityChange.Added -> {
          val artifactBridge = diff.artifactsMap.getDataByEntity(it.entity)!!
          added.add(artifactBridge)
        }
        is EntityChange.Replaced -> {
          // Collect changes and transfer info from the modifiable bridge artifact to the original artifact
          val originalArtifact = diff.artifactsMap.getDataByEntity(it.newEntity)!!
          val modifiableArtifact = modifiableToOriginalCopy.getKeysByValue(originalArtifact)!!.single()
          modifiableToOriginalCopy.remove(modifiableArtifact, originalArtifact)
          changed.add(modifiableArtifact)
        }
      }
    }

    modifiableToOriginalCopy.entries.forEach { (modifiable, original) ->
      if (modifiable !== original) {
        changed.add(modifiable)
      }
    }

    val entityStorage = WorkspaceModel.getInstance(project).entityStorage
    added.forEach { bridge ->
      bridge.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      bridge.elementsWithDiff.clear()
    }
    changed.forEach { bridge ->
      bridge.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      bridge.elementsWithDiff.clear()
    }
  }
}

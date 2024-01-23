// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactId
import com.intellij.java.workspace.entities.CompositePackagingElementEntity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.impl.artifacts.ArtifactPointerManagerImpl
import com.intellij.packaging.impl.artifacts.ArtifactUtil
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.VALID_ARTIFACT_CONDITION
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.mutableArtifactsMap
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.internal
import com.intellij.platform.diagnostic.telemetry.Compiler
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.mapInPlace
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

class ArtifactModifiableModelBridge(
  private val project: Project,
  internal val diff: MutableEntityStorage,
  private val manager: ArtifactManagerBridge,
) : ModifiableArtifactModel {

  internal val modifiableToOriginal = BidirectionalMap<ArtifactBridge, ArtifactBridge>()
  private val eventDispatcher = EventDispatcher.create(ArtifactListener::class.java)

  internal val elementsWithDiff = mutableSetOf<PackagingElement<*>>()

  private val versionedOnBuilder = VersionedEntityStorageOnBuilder(diff)

  override fun getArtifacts(): Array<ArtifactBridge> = getArtifactsMs.addMeasuredTimeMillis {
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
    return@addMeasuredTimeMillis artifacts.mapInPlace { modifiableToOriginal.getKeysByValue(it)?.singleOrNull() ?: it }
  }

  override fun findArtifact(name: String): Artifact? = findArtifactMs.addMeasuredTimeMillis {
    val artifactEntity = diff.resolve(ArtifactId(name)) ?: return null

    val newBridges = mutableListOf<ArtifactBridge>()
    val bridge = diff.artifactsMap.getDataByEntity(artifactEntity)
                 ?: createArtifactBridge(artifactEntity, versionedOnBuilder, project).also {
                   newBridges.add(it)
                   manager.artifactWithDiffs.add(it)
                 }
    addBridgesToDiff(newBridges, diff)

    return@addMeasuredTimeMillis modifiableToOriginal.getKeysByValue(bridge)?.singleOrNull() ?: bridge
  }

  override fun getArtifactByOriginal(artifact: Artifact): Artifact {
    return modifiableToOriginal.getKeysByValue(artifact as ArtifactBridge)?.singleOrNull() ?: artifact
  }

  override fun getOriginalArtifact(artifact: Artifact): Artifact {
    return modifiableToOriginal[artifact as ArtifactBridge] ?: artifact
  }

  override fun getArtifactsByType(type: ArtifactType): Collection<Artifact> = getArtifactsByTypeMs.addMeasuredTimeMillis {
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

  override fun addArtifact(name: String,
                           artifactType: ArtifactType,
                           rootElement: CompositePackagingElement<*>,
                           externalSource: ProjectModelExternalSource?): ModifiableArtifact = addArtifactMs.addMeasuredTimeMillis {
    val uniqueName = generateUniqueName(name)

    val outputPath = ArtifactUtil.getDefaultArtifactOutputPath(uniqueName, project)

    val fileManager = VirtualFileUrlManager.getInstance(project)

    val source = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForArtifact(project, externalSource)

    val rootElementEntity = rootElement.getOrAddEntity(diff, source, project) as CompositePackagingElementEntity
    rootElement.forThisAndFullTree {
      if (!it.hasStorage()) {
        it.setStorage(versionedOnBuilder, project, elementsWithDiff, PackagingElementInitializer)
        elementsWithDiff += it
      }
    }

    val outputUrl = outputPath?.let { fileManager.getOrCreateFromUri(VfsUtilCore.pathToUrl(it)) }
    val artifactEntity = diff addEntity ArtifactEntity(uniqueName, artifactType.id, false, source) {
      this.outputUrl = outputUrl
      this.rootElement = rootElementEntity
    }

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

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun isModified(): Boolean {
    // TODO: 03.02.2021 May give a wrong result
    return (diff as MutableEntityStorageInstrumentation).hasChanges()
  }

  @RequiresWriteLock
  override fun commit() = commitMs.addMeasuredTimeMillis {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    manager.commit(this)
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun dispose() = disposeMs.addMeasuredTimeMillis {
    val artifacts: MutableList<Artifact> = ArrayList()

    val modifiableToOriginalCopy = BidirectionalMap<ArtifactBridge, ArtifactBridge>()
    modifiableToOriginal.forEach { (mod, orig) -> modifiableToOriginalCopy[mod] = orig }

    for (artifact in modifiableToOriginalCopy.keys) {
      if (modifiableToOriginalCopy[artifact] == artifact) {
        artifacts.add(artifact)
      }
    }
    (ArtifactPointerManager.getInstance(project) as ArtifactPointerManagerImpl).disposePointers(artifacts)

    val current = WorkspaceModel.getInstance(project).currentSnapshot
    val changes = (diff as MutableEntityStorageInstrumentation).collectChanges()[ArtifactEntity::class.java] ?: emptyList()

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

    val entityStorage = WorkspaceModel.getInstance(project).internal.entityStorage
    added.forEach { bridge ->
      bridge.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      bridge.elementsWithDiff.clear()
    }
    changed.forEach { bridge ->
      bridge.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      bridge.elementsWithDiff.clear()
    }
  }

  companion object {
    private val getArtifactsMs: AtomicLong = AtomicLong()
    private val findArtifactMs: AtomicLong = AtomicLong()
    private val addArtifactMs: AtomicLong = AtomicLong()
    private val getArtifactsByTypeMs: AtomicLong = AtomicLong()
    private val commitMs: AtomicLong = AtomicLong()
    private val disposeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val getArtifactsGauge = meter.gaugeBuilder("compiler.ArtifactModifiableModelBridge.getArtifacts.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val findArtifactsGauge = meter.gaugeBuilder("compiler.ArtifactModifiableModelBridge.findArtifacts.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val addArtifactGauge = meter.gaugeBuilder("compiler.ArtifactModifiableModelBridge.addArtifact.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val getArtifactsByTypeGauge = meter.gaugeBuilder("compiler.ArtifactModifiableModelBridge.getArtifactsByType.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val commitGauge = meter.gaugeBuilder("compiler.ArtifactModifiableModelBridge.commit.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val disposeGauge = meter.gaugeBuilder("compiler.ArtifactModifiableModelBridge.dispose.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          getArtifactsGauge.record(getArtifactsMs.get())
          findArtifactsGauge.record(findArtifactMs.get())
          addArtifactGauge.record(addArtifactMs.get())
          getArtifactsByTypeGauge.record(getArtifactsByTypeMs.get())
          commitGauge.record(commitMs.get())
          disposeGauge.record(disposeMs.get())
        },
        getArtifactsGauge, findArtifactsGauge, addArtifactGauge, getArtifactsByTypeGauge, commitGauge, disposeGauge
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(Compiler))
    }
  }
}

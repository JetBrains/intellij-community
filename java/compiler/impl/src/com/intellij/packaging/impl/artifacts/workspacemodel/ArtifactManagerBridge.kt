// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.compiler.server.BuildManager
import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactId
import com.intellij.java.workspace.entities.CustomPackagingElementEntity
import com.intellij.java.workspace.entities.modifyEntity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.*
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.elements.PackagingElementResolvingContext
import com.intellij.packaging.impl.artifacts.ArtifactPointerManagerImpl
import com.intellij.packaging.impl.artifacts.DefaultPackagingElementResolvingContext
import com.intellij.packaging.impl.artifacts.InvalidArtifact
import com.intellij.packaging.impl.artifacts.workspacemodel.packaging.elements
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.Compiler
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.storage.*
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

class ArtifactManagerBridge(private val project: Project) : ArtifactManager(), Disposable {

  private val modificationTracker = SimpleModificationTracker()

  private val resolvingContext = DefaultPackagingElementResolvingContext(project)

  internal val artifactWithDiffs: MutableList<ArtifactBridge> = mutableListOf()

  init {
    DynamicArtifactExtensionsLoaderBridge(this).installListeners(this)
  }

  @RequiresReadLock
  override fun getArtifacts(): Array<ArtifactBridge> = getArtifactsMs.addMeasuredTimeMillis {
    initBridges()

    val store = project.workspaceModel.currentSnapshot

    return@addMeasuredTimeMillis store
      .entities(ArtifactEntity::class.java)
      .map { store.artifactsMap.getDataByEntity(it) ?: error("All artifact bridges should be already created at this moment") }
      .filter { VALID_ARTIFACT_CONDITION.value(it) }
      .toList().toTypedArray()
  }

  @RequiresReadLock
  override fun findArtifact(name: String): Artifact? = findArtifactMs.addMeasuredTimeMillis {
    initBridges()

    val store = project.workspaceModel.currentSnapshot

    val artifactEntity = store.resolve(ArtifactId(name)) ?: return null

    return@addMeasuredTimeMillis store.artifactsMap.getDataByEntity(artifactEntity)
                                 ?: error("All artifact bridges should be already created at this moment")
  }

  override fun getArtifactByOriginal(artifact: Artifact): Artifact = artifact

  override fun getOriginalArtifact(artifact: Artifact): Artifact = artifact

  @RequiresReadLock
  override fun getArtifactsByType(type: ArtifactType): List<ArtifactBridge> = getArtifactsByTypeMs.addMeasuredTimeMillis {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertReadAccessAllowed()
    initBridges()

    val store = project.workspaceModel.currentSnapshot
    val typeId = type.id

    return@addMeasuredTimeMillis store
      .entities(ArtifactEntity::class.java)
      .filter { it.artifactType == typeId }
      .map { store.artifactsMap.getDataByEntity(it) ?: error("All artifact bridges should be already created at this moment") }
      .toList()
  }

  @RequiresReadLock
  override fun getAllArtifactsIncludingInvalid(): List<Artifact> {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertReadAccessAllowed()
    initBridges()

    val storage = project.workspaceModel.currentSnapshot

    return storage
      .entities(ArtifactEntity::class.java)
      .map {
        storage.artifactsMap.getDataByEntity(it) ?: error("All artifact bridges should be already created at this moment")
      }
      .toMutableList()
  }

  @RequiresReadLock
  override fun getSortedArtifacts(): Array<ArtifactBridge> {
    val artifacts = this.artifacts

    // TODO: 02.02.2021 Do not sort them each time
    artifacts.sortWith(ARTIFACT_COMPARATOR)
    return artifacts
  }

  override fun createModifiableModel(): ModifiableArtifactModel {
    val storage = project.workspaceModel.currentSnapshot
    return createModifiableModel(MutableEntityStorage.from(storage))
  }

  private fun createModifiableModel(mutableEntityStorage: MutableEntityStorage): ModifiableArtifactModel {
    return ArtifactModifiableModelBridge(project, mutableEntityStorage, this)
  }

  override fun getResolvingContext(): PackagingElementResolvingContext = resolvingContext

  override fun addArtifact(name: String, type: ArtifactType, root: CompositePackagingElement<*>?): Artifact = addArtifactMs.addMeasuredTimeMillis {
    return@addMeasuredTimeMillis WriteAction.compute(ThrowableComputable<ModifiableArtifact, RuntimeException> {
      val model = createModifiableModel()
      val artifact = model.addArtifact(name, type)
      if (root != null) {
        artifact.rootElement = root
      }
      model.commit()
      artifact
    })
  }

  override fun addElementsToDirectory(artifact: Artifact, relativePath: String, elements: Collection<PackagingElement<*>>) {
    val model = createModifiableModel()
    val root = model.getOrCreateModifiableArtifact(artifact).rootElement
    PackagingElementFactory.getInstance().getOrCreateDirectory(root, relativePath).addOrFindChildren(elements)
    WriteAction.run<RuntimeException> { model.commit() }
  }

  override fun addElementsToDirectory(artifact: Artifact, relativePath: String, element: PackagingElement<*>) {
    addElementsToDirectory(artifact, relativePath, listOf(element))
  }

  override fun getModificationTracker(): ModificationTracker {
    return modificationTracker
  }

  @RequiresWriteLock
  fun commit(artifactModel: ArtifactModifiableModelBridge) = commitMs.addMeasuredTimeMillis {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    LOG.trace { "Committing artifact manager bridge. diff: ${artifactModel.diff}" }
    updateCustomElements(artifactModel.diff)

    val current = project.workspaceModel.currentSnapshot
    val changes = artifactModel.diff.collectChanges()[ArtifactEntity::class.java] ?: emptyList()

    val removed = mutableSetOf<ArtifactBridge>()
    val added = mutableListOf<ArtifactBridge>()
    val changed = mutableListOf<Triple<ArtifactBridge, String, ArtifactBridge>>()
    val changedArtifacts: MutableList<ArtifactBridge> = mutableListOf()

    val modifiableToOriginal = BidirectionalMap<ArtifactBridge, ArtifactBridge>()
    artifactModel.modifiableToOriginal.forEach { key, value -> modifiableToOriginal[key] = value }

    changes.forEach {
      when (it) {
        is EntityChange.Removed<*> -> current.artifactsMap.getDataByEntity(it.entity)?.let { it1 -> removed.add(it1) }
        is EntityChange.Added -> Unit
        is EntityChange.Replaced -> {
          // Collect changes and transfer info from the modifiable bridge artifact to the original artifact
          val originalArtifact = artifactModel.diff.artifactsMap.getDataByEntity(it.newEntity)!!
          val modifiableArtifact = modifiableToOriginal.getKeysByValue(originalArtifact)!!.single()
          if (modifiableArtifact !== originalArtifact) {
            changedArtifacts.add(modifiableArtifact)
          }
          originalArtifact.copyFrom(modifiableArtifact)
          changed.add(Triple(originalArtifact, (it.oldEntity as ArtifactEntity).name, modifiableArtifact))
          originalArtifact.setActualStorage()
          modifiableToOriginal.remove(modifiableArtifact, originalArtifact)
        }
      }
    }

    modifiableToOriginal.entries.forEach { (modifiable, original) ->
      if (modifiable !== original) {
        changedArtifacts.add(modifiable)
        val oldName = original.name
        original.copyFrom(modifiable)
        changed.add(Triple(original, oldName, modifiable))
      }
    }

    (ArtifactPointerManager.getInstance(project) as ArtifactPointerManagerImpl).disposePointers(changedArtifacts)

    project.workspaceModel.updateProjectModel("Commit artifact manager") {
      it.addDiff(artifactModel.diff)
    }

    modificationTracker.incModificationCount()

    // Collect changes
    changes.forEach {
      when (it) {
        is EntityChange.Added<*> -> {
          val artifactBridge = artifactModel.diff.artifactsMap.getDataByEntity(it.entity)!!
          added.add(artifactBridge)
          artifactBridge.setActualStorage()
        }
        is EntityChange.Removed<*> -> Unit
        is EntityChange.Replaced<*> -> Unit
      }
    }

    // Set actual storages
    artifactWithDiffs.forEach { it.setActualStorage() }
    artifactWithDiffs.clear()

    val entityStorage = project.workspaceModel.entityStorage
    added.forEach { bridge ->
      bridge.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      bridge.elementsWithDiff.clear()
    }
    changed.forEach { changedItem ->
      changedItem.third.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      changedItem.third.elementsWithDiff.clear()
      changedItem.first.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      changedItem.first.elementsWithDiff.clear()
    }
    artifactModel.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
    artifactModel.elementsWithDiff.clear()

    val publisher: ArtifactListener = project.messageBus.syncPublisher(TOPIC)
    ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring {
      //it's important to send 'removed' events before 'added'. Otherwise when artifacts are reloaded from xml artifact pointers will be damaged
      removed.forEach { publisher.artifactRemoved(it) }
      added.forEach { publisher.artifactAdded(it) }
      changed.forEach { (artifact, oldName) -> publisher.artifactChanged(artifact, oldName) }
    }

    if (changes.isNotEmpty()) {
      BuildManager.getInstance().clearState(project)
    }
  }

  private fun updateCustomElements(diff: MutableEntityStorage) {
    val customEntities = diff.entities(CustomPackagingElementEntity::class.java).toList()
    for (customEntity in customEntities) {
      val packagingElement = diff.elements.getDataByEntity(customEntity) ?: continue
      val state = packagingElement.state ?: continue
      val newState = JDOMUtil.write(XmlSerializer.serialize(state))
      if (newState != customEntity.propertiesXmlTag) {
        diff.modifyEntity(customEntity) {
          this.propertiesXmlTag = newState
        }
      }
    }
  }

  // Initialize all artifact bridges
  @RequiresReadLock
  private fun initBridges() = initBridgesMs.addMeasuredTimeMillis {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val workspaceModel = project.workspaceModel
    val current = workspaceModel.currentSnapshot
    if (current.entitiesAmount(ArtifactEntity::class.java) != current.artifactsMap.size()) {

      synchronized(lock) {
        val currentInSync = workspaceModel.currentSnapshot
        val artifactsMap = currentInSync.artifactsMap

        // Double check
        if (currentInSync.entitiesAmount(ArtifactEntity::class.java) != artifactsMap.size()) {
          val newBridges = currentInSync
            .entities(ArtifactEntity::class.java)
            .mapNotNull {
              if (artifactsMap.getDataByEntity(it) == null) {
                createArtifactBridge(it, workspaceModel.entityStorage, project)
              }
              else null
            }
            .toList()

          (workspaceModel as WorkspaceModelImpl).updateProjectModelSilent("Initialize artifact bridges") {
            addBridgesToDiff(newBridges, it)
          }
        }
      }
    }
  }

  companion object {
    private val lock = Any()
    private const val ARTIFACT_BRIDGE_MAPPING_ID = "intellij.artifacts.bridge"

    val EntityStorage.artifactsMap: ExternalEntityMapping<ArtifactBridge>
      get() = getExternalMapping(ARTIFACT_BRIDGE_MAPPING_ID)

    internal val MutableEntityStorage.mutableArtifactsMap: MutableExternalEntityMapping<ArtifactBridge>
      get() = getMutableExternalMapping(ARTIFACT_BRIDGE_MAPPING_ID)

    private val LOG = logger<ArtifactManagerBridge>()

    val VALID_ARTIFACT_CONDITION: Condition<Artifact> = Condition { it !is InvalidArtifact }

    // -------------- Metrics ----------------------------

    private val getArtifactsMs: AtomicLong = AtomicLong()
    private val findArtifactMs: AtomicLong = AtomicLong()
    private val getArtifactsByTypeMs: AtomicLong = AtomicLong()
    private val addArtifactMs: AtomicLong = AtomicLong()

    private val initBridgesMs: AtomicLong = AtomicLong()
    private val commitMs: AtomicLong = AtomicLong()
    private val dropMappingsMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val getArtifactsCounter = meter.counterBuilder("compiler.ArtifactManagerBridge.getArtifacts.ms")
        .setDescription("Total time spent in method").buildObserver()
      val findArtifactCounter = meter.counterBuilder("compiler.ArtifactManagerBridge.findArtifact.ms")
        .setDescription("Total time spent in method").buildObserver()
      val getArtifactsByTypeCounter = meter.counterBuilder("compiler.ArtifactManagerBridge.getArtifactsByType.ms")
        .setDescription("Total time spent in method").buildObserver()
      val addArtifactCounter = meter.counterBuilder("compiler.ArtifactManagerBridge.addArtifact.ms")
        .setDescription("Total time spent in method").buildObserver()

      val initBridgesCounter = meter.counterBuilder("compiler.ArtifactManagerBridge.initBridges.ms")
        .setDescription("Total time spent in method").buildObserver()
      val commitDurationCounter = meter.counterBuilder("compiler.ArtifactManagerBridge.commit.ms")
        .setDescription("Total time spent in method").buildObserver()
      val dropMappingsCounter = meter.counterBuilder("compiler.ArtifactManagerBridge.dropMappings.ms")
        .setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          getArtifactsCounter.record(getArtifactsMs.get())
          findArtifactCounter.record(findArtifactMs.get())
          getArtifactsByTypeCounter.record(getArtifactsByTypeMs.get())
          addArtifactCounter.record(addArtifactMs.get())

          initBridgesCounter.record(initBridgesMs.get())
          commitDurationCounter.record(commitMs.get())
          dropMappingsCounter.record(dropMappingsMs.get())
        },
        getArtifactsCounter, findArtifactCounter, getArtifactsByTypeCounter, addArtifactCounter,
        initBridgesCounter, commitDurationCounter, dropMappingsCounter
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(Compiler))
    }
  }

  override fun dispose() {
    // Anything here?
  }

  @RequiresWriteLock
  fun dropMappings(selector: (ArtifactEntity) -> Boolean) = dropMappingsMs.addMeasuredTimeMillis {
    // XXX @RequiresWriteLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    (project.workspaceModel as WorkspaceModelImpl).updateProjectModelSilent("Drop artifact mappings") {
      val map = it.mutableArtifactsMap
      it.entities(ArtifactEntity::class.java).filter(selector).forEach { artifact ->
        map.removeMapping(artifact)
      }
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.compiler.server.BuildManager
import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactId
import com.intellij.java.workspace.entities.CustomPackagingElementEntity
import com.intellij.java.workspace.entities.modifyCustomPackagingElementEntity
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
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.Compiler
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.ImmutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CoroutineScope

class ArtifactManagerBridge(private val project: Project, coroutineScope: CoroutineScope) : ArtifactManager() {
  private val modificationTracker = SimpleModificationTracker()

  private val resolvingContext = DefaultPackagingElementResolvingContext(project)

  internal val artifactWithDiffs: MutableList<ArtifactBridge> = mutableListOf()

  init {
    DynamicArtifactExtensionsLoaderBridge(this).installListeners(coroutineScope)
  }

  @RequiresReadLock
  override fun getArtifacts(): Array<ArtifactBridge> = getArtifactsMs.addMeasuredTime {
    initBridges()

    val store = project.workspaceModel.currentSnapshot

    return@addMeasuredTime store
      .entities(ArtifactEntity::class.java)
      .map { store.artifactsMap.getDataByEntity(it) ?: error("All artifact bridges should be already created at this moment") }
      .filter { VALID_ARTIFACT_CONDITION.value(it) }
      .toList().toTypedArray()
  }

  @RequiresReadLock
  override fun findArtifact(name: String): Artifact? = findArtifactMs.addMeasuredTime {
    initBridges()

    val store = project.workspaceModel.currentSnapshot
    val artifactEntity = store.resolve(ArtifactId(name)) ?: return null
    return@addMeasuredTime store.artifactsMap.getDataByEntity(artifactEntity)
                           ?: error("All artifact bridges should be already created at this moment")
  }

  override fun getArtifactByOriginal(artifact: Artifact): Artifact = artifact

  override fun getOriginalArtifact(artifact: Artifact): Artifact = artifact

  @RequiresReadLock
  override fun getArtifactsByType(type: ArtifactType): List<ArtifactBridge> = getArtifactsByTypeMs.addMeasuredTime {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertReadAccessAllowed()
    initBridges()

    val store = project.workspaceModel.currentSnapshot
    val typeId = type.id

    return@addMeasuredTime store
      .entities(ArtifactEntity::class.java)
      .filter { it.artifactType == typeId }
      .map { store.artifactsMap.getDataByEntity(it) ?: error("All artifact bridges should be already created at this moment") }
      .toList()
  }

  @RequiresReadLock
  override fun getAllArtifactsIncludingInvalid(): List<Artifact> {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ThreadingAssertions.assertReadAccess()
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

  override fun addArtifact(name: String, type: ArtifactType, root: CompositePackagingElement<*>?): Artifact = addArtifactMs.addMeasuredTime {
    return@addMeasuredTime WriteAction.compute(ThrowableComputable<ModifiableArtifact, RuntimeException> {
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

  @OptIn(EntityStorageInstrumentationApi::class)
  @RequiresWriteLock
  fun commit(artifactModel: ArtifactModifiableModelBridge) = commitMs.addMeasuredTime {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    LOG.trace { "Committing artifact manager bridge. diff: ${artifactModel.diff}" }
    updateCustomElements(artifactModel.diff)

    val current = project.workspaceModel.currentSnapshot
    val changes = (artifactModel.diff as MutableEntityStorageInstrumentation).collectChanges()[ArtifactEntity::class.java] ?: emptyList()

    val removed = mutableSetOf<ArtifactBridge>()
    val added = mutableListOf<ArtifactBridge>()
    val changed = mutableListOf<Triple<ArtifactBridge, String, ArtifactBridge>>()
    val changedArtifacts: MutableList<ArtifactBridge> = mutableListOf()

    val modifiableToOriginal = BidirectionalMap<ArtifactBridge, ArtifactBridge>()
    for ((key, value) in artifactModel.modifiableToOriginal) {
      modifiableToOriginal[key] = value
    }

    for (change in changes) {
      when (change) {
        is EntityChange.Removed<*> -> current.artifactsMap.getDataByEntity(change.oldEntity)?.let { it1 -> removed.add(it1) }
        is EntityChange.Added -> Unit
        is EntityChange.Replaced -> {
          // Collect changes and transfer info from the modifiable bridge artifact to the original artifact
          val originalArtifact = artifactModel.diff.artifactsMap.getDataByEntity(change.newEntity)!!
          val modifiableArtifact = modifiableToOriginal.getKeysByValue(originalArtifact)!!.single()
          if (modifiableArtifact !== originalArtifact) {
            changedArtifacts.add(modifiableArtifact)
          }
          originalArtifact.copyFrom(modifiableArtifact)
          changed.add(Triple(originalArtifact, (change.oldEntity as ArtifactEntity).name, modifiableArtifact))
          originalArtifact.setActualStorage()
          modifiableToOriginal.remove(modifiableArtifact, originalArtifact)
        }
      }
    }

    for ((modifiable, original) in modifiableToOriginal.entries) {
      if (modifiable !== original) {
        changedArtifacts.add(modifiable)
        val oldName = original.name
        original.copyFrom(modifiable)
        changed.add(Triple(original, oldName, modifiable))
      }
    }

    (ArtifactPointerManager.getInstance(project) as ArtifactPointerManagerImpl).disposePointers(changedArtifacts)

    project.workspaceModel.updateProjectModel("Commit artifact manager") {
      it.applyChangesFrom(artifactModel.diff)
    }

    modificationTracker.incModificationCount()

    // Collect changes
    changes.forEach {
      when (it) {
        is EntityChange.Added<*> -> {
          val artifactBridge = artifactModel.diff.artifactsMap.getDataByEntity(it.newEntity)!!
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

    val entityStorage = (project.workspaceModel as WorkspaceModelInternal).entityStorage
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
      // it's important to send 'removed' events before 'added'.
      // Otherwise, when artifacts are reloaded from XML artifact pointers will be damaged
      removed.forEach { publisher.artifactRemoved(it) }
      added.forEach { publisher.artifactAdded(it) }
      for ((artifact, oldName) in changed) {
        publisher.artifactChanged(artifact, oldName)
      }
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
        diff.modifyCustomPackagingElementEntity(customEntity) {
          this.propertiesXmlTag = newState
        }
      }
    }
  }

  // Initialize all artifact bridges
  @OptIn(EntityStorageInstrumentationApi::class)
  @RequiresReadLock
  private fun initBridges() = initBridgesMs.addMeasuredTime {
    // XXX @RequiresReadLock annotation doesn't work for kt now
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val workspaceModel = project.workspaceModel
    val current = workspaceModel.currentSnapshot as ImmutableEntityStorageInstrumentation
    if (current.entityCount(ArtifactEntity::class.java) != current.artifactsMap.size()) {

      synchronized(lock) {
        val currentInSync = workspaceModel.currentSnapshot as ImmutableEntityStorageInstrumentation
        val artifactsMap = currentInSync.artifactsMap

        // Double check
        if (currentInSync.entityCount(ArtifactEntity::class.java) != artifactsMap.size()) {
          val newBridges = currentInSync
            .entities(ArtifactEntity::class.java)
            .mapNotNull {
              if (artifactsMap.getDataByEntity(it) == null) {
                createArtifactBridge(it, (workspaceModel as WorkspaceModelInternal).entityStorage, project)
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
    private val ARTIFACT_BRIDGE_MAPPING_ID = ExternalMappingKey.create<ArtifactBridge>("intellij.artifacts.bridge")

    val EntityStorage.artifactsMap: ExternalEntityMapping<ArtifactBridge>
      get() = getExternalMapping(ARTIFACT_BRIDGE_MAPPING_ID)

    internal val MutableEntityStorage.mutableArtifactsMap: MutableExternalEntityMapping<ArtifactBridge>
      get() = getMutableExternalMapping(ARTIFACT_BRIDGE_MAPPING_ID)

    private val LOG = logger<ArtifactManagerBridge>()

    val VALID_ARTIFACT_CONDITION: Condition<Artifact> = Condition { it !is InvalidArtifact }

    // -------------- Metrics ----------------------------

    private val getArtifactsMs = MillisecondsMeasurer()
    private val findArtifactMs = MillisecondsMeasurer()
    private val getArtifactsByTypeMs = MillisecondsMeasurer()
    private val addArtifactMs = MillisecondsMeasurer()

    private val initBridgesMs = MillisecondsMeasurer()
    private val commitMs = MillisecondsMeasurer()
    private val dropMappingsMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
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
          getArtifactsCounter.record(getArtifactsMs.asMilliseconds())
          findArtifactCounter.record(findArtifactMs.asMilliseconds())
          getArtifactsByTypeCounter.record(getArtifactsByTypeMs.asMilliseconds())
          addArtifactCounter.record(addArtifactMs.asMilliseconds())

          initBridgesCounter.record(initBridgesMs.asMilliseconds())
          commitDurationCounter.record(commitMs.asMilliseconds())
          dropMappingsCounter.record(dropMappingsMs.asMilliseconds())
        },
        getArtifactsCounter, findArtifactCounter, getArtifactsByTypeCounter, addArtifactCounter,
        initBridgesCounter, commitDurationCounter, dropMappingsCounter
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(Compiler))
    }
  }

  @RequiresWriteLock
  fun dropMappings(selector: (ArtifactEntity) -> Boolean) = dropMappingsMs.addMeasuredTime {
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

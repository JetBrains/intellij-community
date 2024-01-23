// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.java.workspace.entities.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.impl.artifacts.InvalidArtifactType
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.packaging.impl.artifacts.workspacemodel.packaging.elements
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.impl.internal
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.diagnostic.telemetry.Compiler
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.EventDispatcher
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.toExternalSource
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.util.JpsPathUtil
import java.util.concurrent.atomic.AtomicLong

open class ArtifactBridge(
  _artifactId: ArtifactId,
  var entityStorage: VersionedEntityStorage,
  val project: Project,
  val eventDispatcher: EventDispatcher<ArtifactListener>?,
  val originalArtifact: ArtifactBridge?,
) : ModifiableArtifact, UserDataHolderBase() {

  init {
    project.messageBus.connect().subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: VersionedStorageChange) = beforeChangedMs.addMeasuredTimeMillis {
        event.getChanges(ArtifactEntity::class.java).filterIsInstance<EntityChange.Removed<ArtifactEntity>>().forEach {
          if (it.entity.symbolicId != artifactId) return@forEach

          // Artifact may be "re-added" with the same id
          // In this case two artifact bridges exists with the same ArtifactId: one for removed artifact and one for newly created
          // We should make sure that we "disable" removed artifact bridge
          if (artifactId in event.storageAfter
              && event.storageBefore.artifactsMap.getDataByEntity(it.entity) != this@ArtifactBridge
              && event.storageBefore.artifactsMap.getDataByEntity(it.entity) != originalArtifact) {
            return@forEach
          }

          // We inject a builder instead of store because requesting of packaging elements adds new bridges to this builder.
          // If case of storage here, the new bridges will be added to the store.
          entityStorage = VersionedEntityStorageOnBuilder(event.storageBefore.toBuilder())
          assert(artifactId in entityStorage.base) { "Cannot resolve artifact $artifactId." }
        }
      }
    })
  }

  private val diffOrNull: MutableEntityStorage?
    get() {
      val storage = entityStorage
      if (storage is VersionedEntityStorageOnBuilder) {
        return storage.base
      }
      return null
    }

  private val diff: MutableEntityStorage
    get() = diffOrNull ?: error("")

  private var artifactIdRaw = _artifactId

  // Artifacts may be renamed in two ways: using legacy bridge and workspace model directly. If the artifact renamed via the bridge, it's
  //   easy to update this property.
  // For renaming via the model artifact id supposed to be extracted from external mappings.
  // For cases when bridge is created, but not yet saved in external mappings, we extract the actifactId via [artifactIdRaw]
  // There is only one known case when this logic may fail: if modifiable artifact is created (which is not stored in external mappings
  // and not supposed to be) and the name of the artifact is modified directly in diff. However, we assume that this case isn't possible.
  val artifactId: ArtifactId
    get() {
      val symbolicId = (entityStorage.base.artifactsMap.getEntities(this@ArtifactBridge).singleOrNull() as? ArtifactEntity)?.symbolicId
      if (symbolicId != null) {
        artifactIdRaw = symbolicId
      }
      return artifactIdRaw
    }

  val elementsWithDiff = mutableSetOf<PackagingElement<*>>()

  override fun getExternalSource(): ProjectModelExternalSource? {
    val artifactEntity = entityStorage.base.get(artifactId)
    return (artifactEntity.entitySource as? JpsImportedEntitySource)?.toExternalSource()
  }

  override fun getArtifactType(): ArtifactType {
    if (this is InvalidArtifactBridge) return InvalidArtifactType.getInstance()

    val artifactEntity = entityStorage.base.get(artifactId)
    val type = ArtifactType.findById(artifactEntity.artifactType)
    return if (type == null) {
      if (this !is InvalidArtifactBridge) error("")
      InvalidArtifactType.getInstance()
    }
    else type
  }

  final override fun getName(): @NlsSafe String = artifactId.name

  override fun isBuildOnMake(): Boolean {
    val artifactEntity = entityStorage.base.get(artifactId)
    return artifactEntity.includeInProjectBuild
  }

  override fun getRootElement(): CompositePackagingElement<*> {
    val artifactEntity = entityStorage.base.get(artifactId)
    val rootElement = artifactEntity.rootElement!!
    val compositeElement = rootElement.toCompositeElement(project, entityStorage)
    if (!compositeElement.hasStorage() || (compositeElement.storageIsStore() && diffOrNull != null)) {
      compositeElement.setStorage(entityStorage, project, elementsWithDiff, PackagingElementInitializer)
      if (entityStorage is VersionedEntityStorageOnBuilder) {
        elementsWithDiff += compositeElement
      }
    }

    return compositeElement
  }

  override fun getOutputPath(): String? {
    val artifactEntity = entityStorage.base.get(artifactId)
    return artifactEntity.outputUrl?.url?.let { JpsPathUtil.urlToPath(it) }
  }

  override fun getPropertiesProviders(): Collection<ArtifactPropertiesProvider> {
    val artifactType = this.artifactType
    return ArtifactPropertiesProvider.getProviders().filter { it.isAvailableFor(artifactType) }
  }

  override fun getProperties(propertiesProvider: ArtifactPropertiesProvider): ArtifactProperties<*>? {
    val artifactEntity = entityStorage.base.get(artifactId)
    return getArtifactProperties(artifactEntity, this.artifactType, propertiesProvider)
  }

  override fun getOutputFile(): VirtualFile? {
    val artifactEntity = entityStorage.base.get(artifactId)
    val outputUrl = artifactEntity.outputUrl
    return outputUrl?.virtualFile
  }

  override fun getOutputFilePath(): String? {
    val artifactEntity = entityStorage.base.get(artifactId)
    val outputUrl = artifactEntity.outputUrl
    if (outputUrl == null) return null

    val rootElement = artifactEntity.rootElement!!
    val path = JpsPathUtil.urlToPath(outputUrl.url)
    return if (rootElement is ArchivePackagingElementEntity) path + "/" + rootElement.fileName else path
  }

  override fun setBuildOnMake(enabled: Boolean) {
    val entity = diff.get(artifactId)
    diff.modifyEntity(entity) {
      this.includeInProjectBuild = enabled
    }
  }

  override fun setOutputPath(outputPath: String?) {
    val outputUrl = outputPath?.let { VirtualFileUrlManager.getInstance(project).getOrCreateFromUri(VfsUtilCore.pathToUrl(it)) }
    val entity = diff.get(artifactId)
    diff.modifyEntity(entity) {
      this.outputUrl = outputUrl
    }
  }

  override fun setName(name: String) {
    val actualArtifactId = artifactId
    val entity = diff.get(actualArtifactId)
    val oldName = actualArtifactId.name
    diff.modifyEntity(entity) {
      this.name = name
    }
    this.artifactIdRaw = ArtifactId(name)
    eventDispatcher?.multicaster?.artifactChanged(this, oldName)
  }

  override fun setRootElement(root: CompositePackagingElement<*>) {
    val entity = diff.get(artifactId)
    val rootEntity = root.getOrAddEntity(diff, entity.entitySource, project) as CompositePackagingElementEntity

    root.forThisAndFullTree {
      it.setStorage(this.entityStorage, this.project, elementsWithDiff, PackagingElementInitializer)
      if (this.entityStorage is VersionedEntityStorageOnBuilder) {
        elementsWithDiff += it
      }
    }
    val oldRootElement = entity.rootElement!!
    if (oldRootElement != rootEntity) {
      // As we replace old root element with the new one, we should kick builder from old root element
      if (originalArtifact != null) {
        diff.elements.getDataByEntity(oldRootElement)?.let { oldRootBridge ->
          oldRootBridge.forThisAndFullTree {
            it.updateStorage(originalArtifact.entityStorage)
          }
        }
      }
      diff.modifyEntity(entity) {
        this.rootElement = rootEntity
      }
      diff.removeEntity(oldRootElement)
    }
  }

  override fun setProperties(provider: ArtifactPropertiesProvider, properties: ArtifactProperties<*>?) {
    if (properties == null) {
      val entity = diff.get(artifactId)
      val (toBeRemoved, filtered) = entity.customProperties.partition { it.providerType == provider.id }
      if (toBeRemoved.isNotEmpty()) {
        diff.modifyEntity(entity) {
          this.customProperties = filtered
        }
        toBeRemoved.forEach { diff.removeEntity(it) }
      }
    }
    else {
      val tag = properties.propertiesTag()

      val entity = diff.get(artifactId)

      val existingProperty = entity.customProperties.find { it.providerType == provider.id }

      if (existingProperty == null) {
        diff addEntity ArtifactPropertiesEntity(provider.id, entity.entitySource) {
          artifact = entity
          propertiesXmlTag = tag
        }
      }
      else {
        diff.modifyEntity(existingProperty) {
          this.propertiesXmlTag = tag
        }
      }
    }
  }

  override fun setArtifactType(selected: ArtifactType) {
    val entity = diff.get(artifactId)
    diff.modifyEntity(entity) {
      this.artifactType = selected.id
    }

    resetProperties(artifactId, diffOrNull)
  }

  fun copyFrom(modified: ArtifactBridge) {
    this.artifactIdRaw = modified.artifactId
  }

  fun setActualStorage() {
    if (entityStorage is VersionedEntityStorageOnBuilder) {
      entityStorage = WorkspaceModel.getInstance(project).internal.entityStorage
    }
  }

  @NonNls
  override fun toString(): String {
    return "artifact:${artifactId.name}"
  }

  companion object {
    internal fun resetProperties(id: ArtifactId, myDiff: MutableEntityStorage?) {
      // We process only artifact bridges with builder because this logic is applied to the new created artifacts only.
      // If the artifact entity already exists, we suppose that this artifact already has all custom properties filled.
      val builder = myDiff ?: return

      val entity = builder.get(id)
      val previousProperties = entity.customProperties.toList()
      previousProperties.forEach { builder.removeEntity(it) }
    }

    private val beforeChangedMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val beforeChangedGauge = meter.gaugeBuilder("compiler.ArtifactBridge.beforeChanged.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          beforeChangedGauge.record(beforeChangedMs.get())
        },
        beforeChangedGauge,
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(Compiler))
    }
  }
}

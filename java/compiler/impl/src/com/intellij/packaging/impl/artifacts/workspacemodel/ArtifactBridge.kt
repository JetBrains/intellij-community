// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.configurationStore.deserializeInto
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.impl.artifacts.InvalidArtifactType
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.util.EventDispatcher
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.util.JpsPathUtil

open class ArtifactBridge(
  _artifactId: ArtifactId,
  var entityStorage: VersionedEntityStorage,
  val project: Project,
  val eventDispatcher: EventDispatcher<ArtifactListener>?,
) : ModifiableArtifact, UserDataHolderBase() {

  init {
    val busConnection = project.messageBus.connect()
    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(busConnection, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: VersionedStorageChange) {
        event.getChanges(ArtifactEntity::class.java).filterIsInstance<EntityChange.Removed<ArtifactEntity>>().forEach {
          if (it.entity.persistentId() != artifactId) return@forEach

          entityStorage = VersionedEntityStorageOnStorage(event.storageBefore)
          assert(entityStorage.current.resolve(artifactId) != null) { "Cannot resolve artifact $artifactId." }
        }
      }
    })
  }

  private val diffOrNull: WorkspaceEntityStorageBuilder?
    get() {
      val storage = entityStorage
      if (storage is VersionedEntityStorageOnBuilder) {
        return storage.builder
      }
      return null
    }

  private val diff: WorkspaceEntityStorageBuilder
    get() = diffOrNull ?: error("")

  private var artifactIdRaw = _artifactId

  // Artifacts may be renamed in two ways: using legacy bridge and workspace model directly. If the artifact renamed via the bridge, it's
  //   easy to update this property.
  // For renaming via the model artifact id supposed to be extracted from external mappings.
  // For cases when bridge is created, but not yet saved in external mappings, we extract the actifactId via [artifactIdRaw]
  // There is only one known case when this logic may fail: if modifiable artifact is created (which is not stored in external mappings
  // and not supposed to be) and the name of the artifact is modified directly in diff. However, we assume that this case isn't possible.
  val artifactId: ArtifactId
    get() = entityStorage.cachedValue(CachedValue {
      val persistentId = (it.artifactsMap.getEntities(this@ArtifactBridge).singleOrNull() as? ArtifactEntity)?.persistentId()
      if (persistentId != null) {
        artifactIdRaw = persistentId
      }
      artifactIdRaw
    })

  val elementsWithDiff = mutableSetOf<PackagingElement<*>>()

  override fun getExternalSource(): ProjectModelExternalSource? {
    val artifactEntity = entityStorage.current.get(artifactId)
    return (artifactEntity.entitySource as? JpsImportedEntitySource)?.toExternalSource()
  }

  override fun getArtifactType(): ArtifactType {
    if (this is InvalidArtifactBridge) return InvalidArtifactType.getInstance()

    val current = entityStorage.current
    val artifactEntity = current.get(artifactId)
    val type = ArtifactType.findById(artifactEntity.artifactType)
    return if (type == null) {
      if (this !is InvalidArtifactBridge) error("")
      InvalidArtifactType.getInstance()
    }
    else type
  }

  override fun getName(): String {
    return artifactId.name
  }

  override fun isBuildOnMake(): Boolean {
    val artifactEntity = entityStorage.current.get(artifactId)
    return artifactEntity.includeInProjectBuild
  }

  override fun getRootElement(): CompositePackagingElement<*> {
    val current = entityStorage.current
    val artifactEntity = current.get(artifactId)
    val rootElement = artifactEntity.rootElement!!
    val compositeElement = rootElement.toCompositeElement(project, entityStorage.base)
    if (!compositeElement.hasStorage() || (compositeElement.storageIsStore() && diffOrNull != null)) {
      compositeElement.setStorage(entityStorage, project, elementsWithDiff, PackagingElementInitializer)
      if (entityStorage is VersionedEntityStorageOnBuilder) {
        elementsWithDiff += compositeElement
      }
    }

    return compositeElement
  }

  override fun getOutputPath(): String? {
    val artifactEntity = entityStorage.current.get(artifactId)
    return artifactEntity.outputUrl?.url?.let { JpsPathUtil.urlToPath(it) }
  }

  override fun getPropertiesProviders(): MutableCollection<out ArtifactPropertiesProvider> {
    val storage = this.entityStorage.current
    val artifactEntity = storage.get(artifactId)
    return artifactEntity.customProperties.map { ArtifactPropertiesProvider.findById(it.providerType)!! }.toMutableList()
  }

  override fun getProperties(propertiesProvider: ArtifactPropertiesProvider): ArtifactProperties<*>? {
    val storage = this.entityStorage.current
    val artifactEntity = storage.get(artifactId)
    val providerId = propertiesProvider.id
    val customProperty = artifactEntity.customProperties.find { it.providerType == providerId } ?: return null
    val propertiesXmlTag = customProperty.propertiesXmlTag ?: return null

    @Suppress("UNCHECKED_CAST")
    val createdProperties: ArtifactProperties<Any> = propertiesProvider.createProperties(this.artifactType) as ArtifactProperties<Any>
    val state = createdProperties.state!!
    JDOMUtil.load(propertiesXmlTag).deserializeInto(state)

    createdProperties.loadState(state)

    return createdProperties
  }

  override fun getOutputFile(): VirtualFile? {
    val storage = this.entityStorage.current
    val artifactEntity = storage.get(artifactId)
    val outputUrl = artifactEntity.outputUrl
    return outputUrl?.virtualFile
  }

  override fun getOutputFilePath(): String? {
    val storage = this.entityStorage.current
    val artifactEntity = storage.get(artifactId)
    val outputUrl = artifactEntity.outputUrl
    if (outputUrl == null) return null

    val rootElement = artifactEntity.rootElement!!
    val path = JpsPathUtil.urlToPath(outputUrl.url)
    return if (rootElement is ArchivePackagingElementEntity) path + "/" + rootElement.fileName else path
  }

  override fun setBuildOnMake(enabled: Boolean) {
    val entity = diff.get(artifactId)
    diff.modifyEntity(ModifiableArtifactEntity::class.java, entity) {
      this.includeInProjectBuild = enabled
    }
  }

  override fun setOutputPath(outputPath: String?) {
    val outputUrl = outputPath?.let { VirtualFileUrlManager.getInstance(project).fromPath(it) }
    val entity = diff.get(artifactId)
    diff.modifyEntity(ModifiableArtifactEntity::class.java, entity) {
      this.outputUrl = outputUrl
    }
  }

  override fun setName(name: String) {
    val entity = diff.get(artifactId)
    val oldName = artifactId.name
    diff.modifyEntity(ModifiableArtifactEntity::class.java, entity) {
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
      diff.modifyEntity(ModifiableArtifactEntity::class.java, entity) {
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
        diff.modifyEntity(ModifiableArtifactEntity::class.java, entity) {
          this.customProperties = filtered.asSequence()
        }
        toBeRemoved.forEach { diff.removeEntity(it) }
      }
    }
    else {
      val state = properties.state
      val tag = if (state != null) {
        val element = XmlSerializer.serialize(state)
        element.name = "options"
        JDOMUtil.write(element)
      }
      else null

      val entity = diff.get(artifactId)

      val existingProperty = entity.customProperties.find { it.providerType == provider.id }

      if (existingProperty == null) {
        diff.addArtifactPropertiesEntity(entity, provider.id, tag, entity.entitySource)
      }
      else {
        diff.modifyEntity(ModifiableArtifactPropertiesEntity::class.java, existingProperty) {
          this.propertiesXmlTag = tag
        }
      }
    }
  }

  override fun setArtifactType(selected: ArtifactType) {
    val entity = diff.get(artifactId)
    diff.modifyEntity(ModifiableArtifactEntity::class.java, entity) {
      this.artifactType = selected.id
    }
  }

  fun copyFrom(modified: ArtifactBridge) {
    this.artifactIdRaw = modified.artifactId
  }

  fun setActualStorage() {
    if (entityStorage is VersionedEntityStorageOnBuilder) {
      entityStorage = WorkspaceModel.getInstance(project).entityStorage
    }
  }

  @NonNls
  override fun toString(): String {
    return "artifact:${artifactId.name}"
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.configurationStore.deserializeInto
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.JDOMUtil
import com.intellij.packaging.artifacts.*
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.elements.PackagingElementType
import com.intellij.packaging.impl.artifacts.ArtifactModelBase
import com.intellij.packaging.impl.artifacts.ArtifactPointerManagerImpl
import com.intellij.packaging.impl.artifacts.ArtifactUtil
import com.intellij.packaging.impl.artifacts.UnknownPackagingElementTypeException
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.artifactsMap
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.mutableArtifactsMap
import com.intellij.packaging.impl.elements.*
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.mapInPlace
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.jps.util.JpsPathUtil

class ArtifactModifiableModelBridge(
  private val project: Project,
  internal val diff: WorkspaceEntityStorageBuilder,
  private val manager: ArtifactManagerBridge,
) : ModifiableArtifactModel {

  internal val modifiableToOriginal = BidirectionalMap<ArtifactBridge, ArtifactBridge>()
  private val eventDispatcher = EventDispatcher.create(ArtifactListener::class.java)

  internal val elementsWithDiff = mutableSetOf<PackagingElement<*>>()

  override fun getArtifacts(): Array<ArtifactBridge> {
    val newBridges = mutableListOf<ArtifactBridge>()
    val artifacts = diff
      .entities(ArtifactEntity::class.java)
      .map {
        diff.artifactsMap.getDataByEntity(it) ?: createArtifactBridge(it, VersionedEntityStorageOnBuilder(diff), project).also {
          newBridges.add(it)
          manager.artifactWithDiffs.add(it)
        }
      }
      .filter { ArtifactModelBase.VALID_ARTIFACT_CONDITION.value(it) }
      .toList().toTypedArray()
    addBridgesToDiff(newBridges, diff)
    return artifacts.mapInPlace { modifiableToOriginal.getKeysByValue(it)?.singleOrNull() ?: it }
  }

  override fun findArtifact(name: String): Artifact? {
    val artifactEntity = diff.resolve(ArtifactId(name)) ?: return null

    val newBridges = mutableListOf<ArtifactBridge>()
    val bridge = diff.artifactsMap.getDataByEntity(artifactEntity)
                 ?: createArtifactBridge(artifactEntity, VersionedEntityStorageOnBuilder(diff), project).also {
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
      .map {
        diff.artifactsMap.getDataByEntity(it) ?: createArtifactBridge(it, VersionedEntityStorageOnBuilder(diff), project).also {
          newBridges.add(it)
          manager.artifactWithDiffs.add(it)
        }
      }
      .toList()
    addBridgesToDiff(newBridges, diff)
    return artifacts
  }

  override fun getAllArtifactsIncludingInvalid(): MutableList<out Artifact> {
    val newBridges = mutableListOf<ArtifactBridge>()
    val artifacts = diff
      .entities(ArtifactEntity::class.java)
      .map {
        diff.artifactsMap.getDataByEntity(it) ?: createArtifactBridge(it, VersionedEntityStorageOnBuilder(diff), project).also {
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

    val location = getJpsProjectConfigLocation(project)
    val source = if (location != null) {
      // TODO: 05.02.2021 Not really clear about entity source
      val internalSource = JpsFileEntitySource.FileInDirectory(location.baseDirectoryUrl.append(".idea/artifacts"), location)
      if (externalSource != null) {
        JpsImportedEntitySource(internalSource, externalSource.id, project.isExternalStorageEnabled)
      }
      else internalSource
    }
    else {
      NonPersistentEntitySource
    }

    val rootElementEntity = rootElement.getOrAddEntity(diff, source, project) as CompositePackagingElementEntity
    rootElement.forThisAndFullTree {
      if (!it.hasStorage()) {
        it.setStorage(VersionedEntityStorageOnBuilder(diff), project, elementsWithDiff, PackagingElementInitializer)
        elementsWithDiff += it
      }
    }

    val outputUrl = outputPath?.let { fileManager.fromPath(it) }
    val artifactEntity = diff.addArtifactEntity(
      uniqueName, artifactType.id, false,
      outputUrl, rootElementEntity, source
    )

    val persistentId = artifactEntity.persistentId()
    val modifiableArtifact = ArtifactBridge(persistentId, VersionedEntityStorageOnBuilder(diff), project, eventDispatcher)
    modifiableToOriginal[modifiableArtifact] = modifiableArtifact
    diff.mutableArtifactsMap.addMapping(artifactEntity, modifiableArtifact)

    eventDispatcher.multicaster.artifactAdded(modifiableArtifact)

    return modifiableArtifact
  }

  private fun generateUniqueName(baseName: String): String {
    var name = baseName
    var i = 2
    while (true) {
      if (findArtifact(name) == null) {
        return name
      }
      name = baseName + i++
    }
  }

  override fun removeArtifact(artifact: Artifact) {
    artifact as ArtifactBridge
    val original = modifiableToOriginal[artifact]
    if (original != null) {
      modifiableToOriginal.remove(artifact)

      diff.removeEntity(diff.resolve(ArtifactId(artifact.name))!!)
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

    val entity = diff.artifactsMap.getEntities(artifact).singleOrNull() as? ArtifactEntity
    if (entity == null) error("Artifact doesn't exist")
    val artifactId = entity.persistentId()
    val existingModifiableArtifact = modifiableToOriginal.getKeysByValue(artifact)?.singleOrNull()
    if (existingModifiableArtifact != null) return existingModifiableArtifact

    val modifiableArtifact = ArtifactBridge(artifactId, VersionedEntityStorageOnBuilder(diff), project, eventDispatcher)
    modifiableToOriginal[modifiableArtifact] = artifact
    eventDispatcher.multicaster.artifactChanged(modifiableArtifact, artifact.name)
    return modifiableArtifact
  }

  override fun getModifiableCopy(artifact: Artifact?): Artifact? {
    if (artifact == null) return null
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
    return !diff.isEmpty()
  }

  @RequiresWriteLock
  override fun commit() {
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
    added.forEach {
      it.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      it.elementsWithDiff.clear()
    }
    changed.forEach {
      it.elementsWithDiff.forEach { it.setStorage(entityStorage, project, HashSet(), PackagingElementInitializer) }
      it.elementsWithDiff.clear()
    }
  }
}

internal fun PackagingElementEntity.sameTypeWith(type: PackagingElementType<out PackagingElement<*>>): Boolean {
  return when (this) {
    is ModuleOutputPackagingElementEntity -> type == ProductionModuleOutputElementType.ELEMENT_TYPE
    is ModuleTestOutputPackagingElementEntity -> type == TestModuleOutputElementType.ELEMENT_TYPE
    is ModuleSourcePackagingElementEntity -> type == ProductionModuleSourceElementType.ELEMENT_TYPE
    is ArtifactOutputPackagingElementEntity -> type == PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE
    is ExtractedDirectoryPackagingElementEntity -> type == PackagingElementFactoryImpl.EXTRACTED_DIRECTORY_ELEMENT_TYPE
    is FileCopyPackagingElementEntity -> type == PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE
    is DirectoryCopyPackagingElementEntity -> type == PackagingElementFactoryImpl.DIRECTORY_COPY_ELEMENT_TYPE
    is DirectoryPackagingElementEntity -> type == PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE
    is ArchivePackagingElementEntity -> type == PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE
    is ArtifactRootElementEntity -> type == PackagingElementFactoryImpl.ARTIFACT_ROOT_ELEMENT_TYPE
    is LibraryFilesPackagingElementEntity -> type == LibraryElementType.LIBRARY_ELEMENT_TYPE
    is CustomPackagingElementEntity -> this.typeId == type.id
    else -> error("Unexpected branch. $this")
  }
}

internal fun CompositePackagingElementEntity.toCompositeElement(
  project: Project,
  storage: WorkspaceEntityStorage,
  addToMapping: Boolean = true,
): CompositePackagingElement<*> {
  val existing = storage.elements.getDataByEntity(this)
  if (existing != null) return existing as CompositePackagingElement<*>

  val element = when (this) {
    is DirectoryPackagingElementEntity -> {
      val element = DirectoryPackagingElement(this.directoryName)
      this.children.pushTo(element, project, storage)
      element
    }
    is ArchivePackagingElementEntity -> {
      val mapping = storage.getExternalMapping<CompositePackagingElement<*>>("intellij.artifacts.packaging.elements")
      val data = mapping.getDataByEntity(this)
      if (data != null) {
        return data
      }

      val element = ArchivePackagingElement(this.fileName)
      this.children.pushTo(element, project, storage)
      element
    }
    is ArtifactRootElementEntity -> {
      val element = ArtifactRootElementImpl()
      this.children.pushTo(element, project, storage)
      element
    }
    is CustomPackagingElementEntity -> {
      val unpacked = unpackCustomElement(storage, project)
      if (unpacked !is CompositePackagingElement<*>) {
        error("Expected composite packaging element")
      }
      unpacked
    }
    else -> unknownElement()
  }
  if (addToMapping) {
    if (storage is WorkspaceEntityStorageBuilder) {
      val mutableMapping = storage.mutableElements
      mutableMapping.addMapping(this, element)
    }
    else {
      WorkspaceModel.getInstance(project).updateProjectModelSilent {
        val mutableMapping = it.mutableElements
        mutableMapping.addMapping(this, element)
      }
    }
  }
  return element
}

fun PackagingElementEntity.toElement(project: Project, storage: WorkspaceEntityStorage): PackagingElement<*> {
  val existing = storage.elements.getDataByEntity(this)
  if (existing != null) return existing

  val element = when (this) {
    is ModuleOutputPackagingElementEntity -> {
      val module = this.module
      if (module != null) {
        val modulePointer = ModulePointerManager.getInstance(project).create(module.name)
        ProductionModuleOutputPackagingElement(project, modulePointer)
      }
      else {
        ProductionModuleOutputPackagingElement(project)
      }
    }
    is ModuleTestOutputPackagingElementEntity -> {
      val module = this.module
      if (module != null) {
        val modulePointer = ModulePointerManager.getInstance(project).create(module.name)
        TestModuleOutputPackagingElement(project, modulePointer)
      }
      else {
        TestModuleOutputPackagingElement(project)
      }
    }
    is ModuleSourcePackagingElementEntity -> {
      val module = this.module
      if (module != null) {
        val modulePointer = ModulePointerManager.getInstance(project).create(module.name)
        ProductionModuleSourcePackagingElement(project, modulePointer)
      }
      else {
        ProductionModuleSourcePackagingElement(project)
      }
    }
    is ArtifactOutputPackagingElementEntity -> {
      val artifact = this.artifact
      if (artifact != null) {
        val artifactPointer = ArtifactPointerManager.getInstance(project).createPointer(artifact.name)
        ArtifactPackagingElement(project, artifactPointer)
      }
      else {
        ArtifactPackagingElement(project)
      }
    }
    is ExtractedDirectoryPackagingElementEntity -> {
      val pathInArchive = this.pathInArchive
      val archive = this.filePath
      ExtractedDirectoryPackagingElement(JpsPathUtil.urlToPath(archive.url), pathInArchive)
    }
    is FileCopyPackagingElementEntity -> {
      val file = this.filePath
      val renamedOutputFileName = this.renamedOutputFileName
      if (file != null) {
        if (renamedOutputFileName != null) {
          FileCopyPackagingElement(JpsPathUtil.urlToPath(file.url), renamedOutputFileName)
        }
        else {
          FileCopyPackagingElement(JpsPathUtil.urlToPath(file.url))
        }
      }
      else {
        FileCopyPackagingElement()
      }
    }
    is DirectoryCopyPackagingElementEntity -> {
      val directory = this.filePath
      if (directory != null) {
        DirectoryCopyPackagingElement(JpsPathUtil.urlToPath(directory.url))
      }
      else {
        DirectoryCopyPackagingElement()
      }
    }
    is ArchivePackagingElementEntity -> this.toCompositeElement(project, storage, false)
    is DirectoryPackagingElementEntity -> this.toCompositeElement(project, storage, false)
    is ArtifactRootElementEntity -> this.toCompositeElement(project, storage, false)
    is LibraryFilesPackagingElementEntity -> {
      val mapping = storage.getExternalMapping<PackagingElement<*>>("intellij.artifacts.packaging.elements")
      val data = mapping.getDataByEntity(this)
      if (data != null) {
        return data
      }

      val library = this.library
      if (library != null) {
        val tableId = library.tableId
        val moduleName = if (tableId is LibraryTableId.ModuleLibraryTableId) tableId.moduleId.name else null
        LibraryPackagingElement(tableId.level, library.name, moduleName)
      }
      else {
        LibraryPackagingElement()
      }
    }
    is CustomPackagingElementEntity -> unpackCustomElement(storage, project)
    else -> unknownElement()
  }

  if (storage is WorkspaceEntityStorageBuilder) {
    val mutableMapping = storage.mutableElements
    mutableMapping.addIfAbsent(this, element)
  }
  else {
    WorkspaceModel.getInstance(project).updateProjectModelSilent {
      val mutableMapping = it.mutableElements
      mutableMapping.addIfAbsent(this, element)
    }
  }
  return element
}

private fun CustomPackagingElementEntity.unpackCustomElement(storage: WorkspaceEntityStorage,
                                                             project: Project): PackagingElement<*> {
  val mapping = storage.getExternalMapping<PackagingElement<*>>("intellij.artifacts.packaging.elements")
  val data = mapping.getDataByEntity(this)
  if (data != null) {
    return data
  }

  // TODO: 09.04.2021 It should be invalid artifact instead of error
  val elementType = PackagingElementFactory.getInstance().findElementType(this.typeId)
                    ?: throw UnknownPackagingElementTypeException(this.typeId)
  val packagingElement = elementType.createEmpty(project) as PackagingElement<Any>
  val state = packagingElement.state
  if (state != null) {
    val element = JDOMUtil.load(this.propertiesXmlTag)
    element.deserializeInto(state)
    packagingElement.loadState(state)
  }
  if (packagingElement is CompositePackagingElement<*>) {
    this.children.pushTo(packagingElement, project, storage)
  }
  return packagingElement
}

private fun PackagingElementEntity.unknownElement(): Nothing {
  error("Unknown packaging element entity: $this")
}

fun Sequence<PackagingElementEntity>.pushTo(element: CompositePackagingElement<*>, project: Project, storage: WorkspaceEntityStorage) {
  val children = this.map { it.toElement(project, storage) }.toList()
  children.reversed().forEach { element.addFirstChild(it) }
}

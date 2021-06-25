// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.project.Project
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider
import com.intellij.packaging.artifacts.ArtifactType
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.elements.PackagingElementType
import com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge.Companion.mutableArtifactsMap
import com.intellij.packaging.impl.elements.*
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*

internal fun addBridgesToDiff(newBridges: List<ArtifactBridge>, builder: WorkspaceEntityStorageBuilder) {
  for (newBridge in newBridges) {
    val artifactEntity = builder.resolve(newBridge.artifactId) ?: continue
    builder.mutableArtifactsMap.addMapping(artifactEntity, newBridge)
  }
}

internal fun createArtifactBridge(it: ArtifactEntity, entityStorage: VersionedEntityStorage, project: Project): ArtifactBridge {
  if (ArtifactType.findById(it.artifactType) == null) {
    return InvalidArtifactBridge(it.persistentId(), entityStorage, project, null,
                                 JavaCompilerBundle.message("unknown.artifact.type.0", it.artifactType))
  }

  fun findMissingArtifactType(element: PackagingElementEntity): String? {
    if (element is CustomPackagingElementEntity) {
      if (PackagingElementFactory.getInstance().findElementType(element.typeId) == null) {
        return element.typeId
      }
    }

    if (element is CompositePackagingElementEntity) {
      element.children.forEach { child ->
        val artifactType = findMissingArtifactType(child)
        if (artifactType != null) return artifactType
      }
    }
    return null
  }

  val missingArtifactType = findMissingArtifactType(it.rootElement!!)
  if (missingArtifactType != null) {
    return InvalidArtifactBridge(it.persistentId(), entityStorage, project, null,
                                 JavaCompilerBundle.message("unknown.element.0", missingArtifactType))
  }

  val unknownProperty = it.customProperties.firstOrNull { ArtifactPropertiesProvider.findById(it.providerType) == null }
  if (unknownProperty != null) {
    return InvalidArtifactBridge(it.persistentId(), entityStorage, project, null,
                                 JavaCompilerBundle.message("unknown.artifact.properties.0", unknownProperty))
  }

  return ArtifactBridge(it.persistentId(), entityStorage, project, null)
}

fun PackagingElement<*>.forThisAndFullTree(action: (PackagingElement<*>) -> Unit) {
  action(this)
  if (this is CompositePackagingElement<*>) {
    this.children.forEach {
      if (it is CompositePackagingElement<*>) {
        it.forThisAndFullTree(action)
      }
      else {
        action(it)
      }
    }
  }
}

fun WorkspaceEntityStorage.get(id: ArtifactId): ArtifactEntity = this.resolve(id) ?: error("Cannot find artifact by id: ${id.name}")

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

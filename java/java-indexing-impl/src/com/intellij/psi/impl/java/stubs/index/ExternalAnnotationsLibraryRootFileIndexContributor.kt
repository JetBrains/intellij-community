// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index

import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.LibraryFileSetData

/**
 * Registers external annotation roots from libraries in the [WorkspaceFileIndex][com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex]
 * so that [ExternalAnnotationsIndex] can index `annotations.xml` files and keep up with changes.
 *
 * The platform's [LibraryRootFileIndexContributor][com.intellij.workspaceModel.core.fileIndex.impl.LibraryRootFileIndexContributor]
 * only handles COMPILED and SOURCES roots; this contributor covers the ANNOTATIONS root type
 * registered by the Java plugin via [com.intellij.openapi.roots.AnnotationOrderRootType].
 *
 * @see ExternalAnnotationsSdkRootFileIndexContributor
 */
class ExternalAnnotationsLibraryRootFileIndexContributor : WorkspaceFileIndexContributor<LibraryEntity> {
  override val entityClass: Class<LibraryEntity>
    get() = LibraryEntity::class.java

  override fun registerFileSets(entity: LibraryEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val libraryId = entity.symbolicId
    if (libraryId.tableId !is LibraryTableId.ModuleLibraryTableId && !storage.hasReferrers(libraryId)) return
    for (root in entity.roots) {
      if (root.type.name == AnnotationOrderRootType.ANNOTATIONS_ID && root.inclusionOptions == LibraryRoot.InclusionOptions.ROOT_ITSELF) {
        // annotation roots don't participate in JVM package resolution
        registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL, entity, LibraryAnnotationsFileSetData(libraryId))
      }
    }
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<LibraryEntity>>
    get() = listOf(DependencyDescription.OnReference(LibraryId::class.java))
}

private data class LibraryAnnotationsFileSetData(override val libraryId: LibraryId?) : LibraryFileSetData
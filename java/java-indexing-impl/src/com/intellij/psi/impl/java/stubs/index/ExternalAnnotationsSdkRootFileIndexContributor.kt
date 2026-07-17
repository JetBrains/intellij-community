// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index

import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.SdkFileSetData
import com.intellij.workspaceModel.core.fileIndex.impl.isProjectSdk

/**
 * Registers external annotation roots from SDKs in the [WorkspaceFileIndex][com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex]
 * so that [ExternalAnnotationsIndex] can index `annotations.xml` files and keep up with changes.
 *
 * The platform's [LibraryRootFileIndexContributor][com.intellij.workspaceModel.core.fileIndex.impl.SdkEntityFileIndexContributor]
 * only handles CLASSES and SOURCES roots; this contributor covers the ANNOTATIONS root type
 * registered by the Java plugin via [com.intellij.openapi.roots.AnnotationOrderRootType].
 *
 * @see ExternalAnnotationsLibraryRootFileIndexContributor
 */
class ExternalAnnotationsSdkRootFileIndexContributor : WorkspaceFileIndexContributor<SdkEntity> {
  override val entityClass: Class<SdkEntity>
    get() = SdkEntity::class.java

  override fun registerFileSets(entity: SdkEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    if (!storage.isProjectSdk(entity) && !storage.hasReferrers(entity.symbolicId)) return
    for (root in entity.roots) {
      if (root.type.name == AnnotationOrderRootType.SDK_ROOT_NAME) {
        registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL, entity, SdkAnnotationsFileSetData(entity.symbolicId))
      }
    }
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<SdkEntity>>
    get() = listOf(DependencyDescription.OnReference(SdkId::class.java))
}

private data class SdkAnnotationsFileSetData(override val sdkId: SdkId) : SdkFileSetData
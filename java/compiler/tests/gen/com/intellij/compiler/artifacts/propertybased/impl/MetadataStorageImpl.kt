// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.artifacts.propertybased.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.compiler.artifacts.propertybased.TestEntitySource",
                                                     properties = listOf(
                                                       OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                           name = "virtualFileUrl",
                                                                           valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                             isNullable = true,
                                                                             typeMetadata = FinalClassMetadata.KnownClass(
                                                                               fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                           withDefault = false)),
                                                     supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.compiler.artifacts.workspaceModel.ArtifactTest\$MySource",
                                                     properties = listOf(
                                                       OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false,
                                                                           name = "virtualFileUrl",
                                                                           valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                             isNullable = true,
                                                                             typeMetadata = FinalClassMetadata.KnownClass(
                                                                               fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                           withDefault = false)),
                                                     supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ObjectMetadata(
      fqName = "com.intellij.compiler.artifacts.workspaceModel.ArtifactWatchRootsTest\$MySource", properties = listOf(
        OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl",
                            valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true,
                                                                                typeMetadata = FinalClassMetadata.KnownClass(
                                                                                  fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                            withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1183542926)
    addMetadataHash(typeFqn = "com.intellij.compiler.artifacts.propertybased.TestEntitySource", metadataHash = 1244580128)
    addMetadataHash(typeFqn = "com.intellij.compiler.artifacts.workspaceModel.ArtifactTest\$MySource", metadataHash = -905168511)
    addMetadataHash(typeFqn = "com.intellij.compiler.artifacts.workspaceModel.ArtifactWatchRootsTest\$MySource", metadataHash = 457239199)
  }

}

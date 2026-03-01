// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.testEntities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.*

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
    val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")

    var typeMetadata: StorageTypeMetadata

    typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.util.indexing.testEntities.TestModuleEntitySource",
                                                     properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                             isKey = false,
                                                                                             isOpen = false,
                                                                                             name = "virtualFileUrl",
                                                                                             valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                               isNullable = true,
                                                                                               typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                 fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                             withDefault = false)),
                                                     supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.util.indexing.testEntities.ReferredTestEntityId",
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "name",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "presentableName",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.util.indexing.testEntities.WithReferenceTestEntityId",
                                                    properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "name",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false),
                                                                        OwnPropertyMetadata(isComputable = false,
                                                                                            isKey = false,
                                                                                            isOpen = false,
                                                                                            name = "presentableName",
                                                                                            valueType = primitiveTypeStringNotNullable,
                                                                                            withDefault = false)),
                                                    supertypes = listOf("com.intellij.platform.workspace.storage.SymbolicEntityId"))

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.ChildTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.ChildTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.util.indexing.testEntities.ParentTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "customChildProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.ExcludedTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.ExcludedTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "root",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.IndexingTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.IndexingTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "roots",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "excludedRoots",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.IndexingTestEntity2",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.IndexingTestEntity2Data",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "roots",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "excludedRoots",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                      fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.NonIndexableTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.NonIndexableTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "root",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.NonRecursiveTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.NonRecursiveTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "root",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.OneMoreWithReferenceTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.OneMoreWithReferenceTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "references",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.util.indexing.testEntities.DependencyItem",
                                                                                                                      properties = listOf(
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "reference",
                                                                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                            isNullable = false,
                                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                              fqName = "com.intellij.util.indexing.testEntities.ReferredTestEntityId",
                                                                                                                              properties = listOf(
                                                                                                                                OwnPropertyMetadata(
                                                                                                                                  isComputable = false,
                                                                                                                                  isKey = false,
                                                                                                                                  isOpen = false,
                                                                                                                                  name = "name",
                                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                                  withDefault = false),
                                                                                                                                OwnPropertyMetadata(
                                                                                                                                  isComputable = false,
                                                                                                                                  isKey = false,
                                                                                                                                  isOpen = false,
                                                                                                                                  name = "presentableName",
                                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                                  withDefault = false)),
                                                                                                                              supertypes = listOf(
                                                                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                                                                          withDefault = false)),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.ParentTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.ParentTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "child",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.util.indexing.testEntities.ChildTestEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "secondChild",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.util.indexing.testEntities.SiblingEntity",
                                                                                                                        isChild = true,
                                                                                                                        isNullable = true),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "customParentProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parentEntityRoot",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.ReferredTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.ReferredTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "name",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "file",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.util.indexing.testEntities.ReferredTestEntityId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "name",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "presentableName",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.SiblingEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.SiblingEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "parent",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                                                        entityFqName = "com.intellij.util.indexing.testEntities.ParentTestEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "customSiblingProperty",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.util.indexing.testEntities.WithReferenceTestEntity",
                                  entityDataFqName = "com.intellij.util.indexing.testEntities.impl.WithReferenceTestEntityData",
                                  supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity",
                                                      "com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId"),
                                  properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "entitySource",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.KnownClass(
                                                                                                                                fqName = "com.intellij.platform.workspace.storage.EntitySource")),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "name",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.util.indexing.testEntities.WithReferenceTestEntityId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "name",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "presentableName",
                                                                                                                                    valueType = primitiveTypeStringNotNullable,
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "references",
                                                                          valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(
                                                                            ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                    typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                      fqName = "com.intellij.util.indexing.testEntities.DependencyItem",
                                                                                                                      properties = listOf(
                                                                                                                        OwnPropertyMetadata(
                                                                                                                          isComputable = false,
                                                                                                                          isKey = false,
                                                                                                                          isOpen = false,
                                                                                                                          name = "reference",
                                                                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                            isNullable = false,
                                                                                                                            typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                              fqName = "com.intellij.util.indexing.testEntities.ReferredTestEntityId",
                                                                                                                              properties = listOf(
                                                                                                                                OwnPropertyMetadata(
                                                                                                                                  isComputable = false,
                                                                                                                                  isKey = false,
                                                                                                                                  isOpen = false,
                                                                                                                                  name = "name",
                                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                                  withDefault = false),
                                                                                                                                OwnPropertyMetadata(
                                                                                                                                  isComputable = false,
                                                                                                                                  isKey = false,
                                                                                                                                  isOpen = false,
                                                                                                                                  name = "presentableName",
                                                                                                                                  valueType = primitiveTypeStringNotNullable,
                                                                                                                                  withDefault = false)),
                                                                                                                              supertypes = listOf(
                                                                                                                                "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                                                                          withDefault = false)),
                                                                                                                      supertypes = listOf()))),
                                                                                                                          primitive = primitiveTypeListNotNullable),
                                                                          withDefault = false)),
                                  extProperties = listOf(),
                                  isAbstract = false)

    addMetadata(typeMetadata)
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.ChildTestEntity", metadataHash = 1915844221)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.ExcludedTestEntity", metadataHash = -473796998)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.IndexingTestEntity", metadataHash = 1477145119)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.IndexingTestEntity2", metadataHash = -1127209129)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.NonIndexableTestEntity", metadataHash = -1869731996)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.NonRecursiveTestEntity", metadataHash = 1038466544)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.OneMoreWithReferenceTestEntity", metadataHash = -1353528005)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.ParentTestEntity", metadataHash = -2109221898)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.ReferredTestEntity", metadataHash = 471114380)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.SiblingEntity", metadataHash = 1415423063)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.WithReferenceTestEntity", metadataHash = 579056395)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.DependencyItem", metadataHash = 829948846)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.ReferredTestEntityId", metadataHash = 1649223808)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.WithReferenceTestEntityId", metadataHash = 243998870)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 1279624819)
    addMetadataHash(typeFqn = "com.intellij.util.indexing.testEntities.TestModuleEntitySource", metadataHash = -1815413801)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = 352730204)
  }
}

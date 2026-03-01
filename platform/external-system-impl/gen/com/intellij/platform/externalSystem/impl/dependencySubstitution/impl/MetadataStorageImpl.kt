// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
  override fun initializeMetadata() {
    val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
    val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")

    var typeMetadata: StorageTypeMetadata

    typeMetadata =
      FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntitySource",
                                        properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                isKey = false,
                                                                                isOpen = false,
                                                                                name = "virtualFileUrl",
                                                                                valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                  isNullable = true,
                                                                                  typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                withDefault = false)),
                                        supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

    addMetadata(typeMetadata)

    typeMetadata =
      FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                       properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "codeCache",
                                                                               valueType = primitiveTypeIntNotNullable,
                                                                               withDefault = false),
                                                           OwnPropertyMetadata(isComputable = false,
                                                                               isKey = false,
                                                                               isOpen = false,
                                                                               name = "externalProjectPath",
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

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntity",
                                  entityDataFqName = "com.intellij.platform.externalSystem.impl.dependencySubstitution.impl.DependencySubstitutionEntityData",
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
                                                                          name = "owner",
                                                                          valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                        entityFqName = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                                                                                                                        isChild = false,
                                                                                                                        isNullable = false),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "library",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.jps.entities.LibraryId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "codeCache",
                                                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                                                    withDefault = false),
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
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "tableId",
                                                                                                                                    valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                      isNullable = false,
                                                                                                                                      typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(
                                                                                                                                        fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                                                                        subclasses = listOf(
                                                                                                                                          FinalClassMetadata.ObjectMetadata(
                                                                                                                                            fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId",
                                                                                                                                            properties = listOf(
                                                                                                                                              OwnPropertyMetadata(
                                                                                                                                                isComputable = false,
                                                                                                                                                isKey = false,
                                                                                                                                                isOpen = false,
                                                                                                                                                name = "level",
                                                                                                                                                valueType = primitiveTypeStringNotNullable,
                                                                                                                                                withDefault = false)),
                                                                                                                                            supertypes = listOf(
                                                                                                                                              "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                                                                              "java.io.Serializable")),
                                                                                                                                          FinalClassMetadata.ClassMetadata(
                                                                                                                                            fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId",
                                                                                                                                            properties = listOf(
                                                                                                                                              OwnPropertyMetadata(
                                                                                                                                                isComputable = false,
                                                                                                                                                isKey = false,
                                                                                                                                                isOpen = false,
                                                                                                                                                name = "level",
                                                                                                                                                valueType = primitiveTypeStringNotNullable,
                                                                                                                                                withDefault = false)),
                                                                                                                                            supertypes = listOf(
                                                                                                                                              "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                                                                              "java.io.Serializable")),
                                                                                                                                          FinalClassMetadata.ClassMetadata(
                                                                                                                                            fqName = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId",
                                                                                                                                            properties = listOf(
                                                                                                                                              OwnPropertyMetadata(
                                                                                                                                                isComputable = false,
                                                                                                                                                isKey = false,
                                                                                                                                                isOpen = false,
                                                                                                                                                name = "level",
                                                                                                                                                valueType = primitiveTypeStringNotNullable,
                                                                                                                                                withDefault = false),
                                                                                                                                              OwnPropertyMetadata(
                                                                                                                                                isComputable = false,
                                                                                                                                                isKey = false,
                                                                                                                                                isOpen = false,
                                                                                                                                                name = "moduleId",
                                                                                                                                                valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                                                                                  isNullable = false,
                                                                                                                                                  typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                                    fqName = "com.intellij.platform.workspace.jps.entities.ModuleId",
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
                                                                                                                                            supertypes = listOf(
                                                                                                                                              "com.intellij.platform.workspace.jps.entities.LibraryTableId",
                                                                                                                                              "java.io.Serializable"))),
                                                                                                                                        supertypes = listOf(
                                                                                                                                          "java.io.Serializable"))),
                                                                                                                                    withDefault = false)),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "com.intellij.platform.workspace.storage.SymbolicEntityId"))),
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = false,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "module",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.jps.entities.ModuleId",
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
                                                                          name = "scope",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.EnumClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.workspace.jps.entities.DependencyScope",
                                                                                                                                properties = listOf(),
                                                                                                                                supertypes = listOf(
                                                                                                                                  "java.io.Serializable",
                                                                                                                                  "kotlin.Comparable",
                                                                                                                                  "kotlin.Enum"),
                                                                                                                                values = listOf(
                                                                                                                                  "COMPILE",
                                                                                                                                  "PROVIDED",
                                                                                                                                  "RUNTIME",
                                                                                                                                  "TEST"))),
                                                                          withDefault = false)),
                                  extProperties = listOf(ExtPropertyMetadata(isComputable = false,
                                                                             isOpen = false,
                                                                             name = "substitutions",
                                                                             receiverFqn = "com.intellij.platform.workspace.jps.entities.ModuleEntity",
                                                                             valueType = ValueTypeMetadata.EntityReference(connectionType = ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                                                                           entityFqName = "com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntity",
                                                                                                                           isChild = true,
                                                                                                                           isNullable = false),
                                                                             withDefault = false)),
                                  isAbstract = false)

    addMetadata(typeMetadata)

    typeMetadata = EntityMetadata(fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity",
                                  entityDataFqName = "com.intellij.platform.externalSystem.impl.workspaceModel.impl.ExternalProjectEntityData",
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
                                                                          name = "externalProjectPath",
                                                                          valueType = primitiveTypeStringNotNullable,
                                                                          withDefault = false),
                                                      OwnPropertyMetadata(isComputable = true,
                                                                          isKey = false,
                                                                          isOpen = false,
                                                                          name = "symbolicId",
                                                                          valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false,
                                                                                                                              typeMetadata = FinalClassMetadata.ClassMetadata(
                                                                                                                                fqName = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                                                                                                                                properties = listOf(
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "codeCache",
                                                                                                                                    valueType = primitiveTypeIntNotNullable,
                                                                                                                                    withDefault = false),
                                                                                                                                  OwnPropertyMetadata(
                                                                                                                                    isComputable = false,
                                                                                                                                    isKey = false,
                                                                                                                                    isOpen = false,
                                                                                                                                    name = "externalProjectPath",
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
  }

  override fun initializeMetadataHash() {
    addMetadataHash(typeFqn = "com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntity",
                    metadataHash = -225585124)
    addMetadataHash(typeFqn = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity", metadataHash = -365334502)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryId", metadataHash = 1783065412)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId", metadataHash = 1939585583)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$GlobalLibraryTableId", metadataHash = 105250347)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ModuleLibraryTableId",
                    metadataHash = -1712287206)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.ModuleId", metadataHash = 369441961)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.LibraryTableId\$ProjectLibraryTableId",
                    metadataHash = 824092854)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.entities.DependencyScope", metadataHash = 1399286845)
    addMetadataHash(typeFqn = "com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId",
                    metadataHash = -1650256201)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -849218479)
    addMetadataHash(typeFqn = "com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionEntitySource",
                    metadataHash = 1705395611)
    addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.SymbolicEntityId", metadataHash = -1188197817)
  }
}

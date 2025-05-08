package com.intellij.openapi.externalSystem.settings.workspaceModel.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        val primitiveTypeListNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "List")
        val primitiveTypeMapNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Map")

        var typeMetadata: StorageTypeMetadata

        typeMetadata = FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

        addMetadata(typeMetadata)

        typeMetadata = EntityMetadata(fqName = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity", entityDataFqName = "com.intellij.openapi.externalSystem.settings.workspaceModel.impl.ExternalProjectsBuildClasspathEntityData", supertypes = listOf("com.intellij.platform.workspace.storage.WorkspaceEntity"), properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entitySource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.EntitySource")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectsBuildClasspath", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable,
ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectBuildClasspathEntity", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "moduleBuildClasspath", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable,
ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalModuleBuildClasspathEntity", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "entries", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "path", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf()))), primitive = primitiveTypeMapNotNullable), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "name", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectBuildClasspath", valueType = ValueTypeMetadata.ParameterizedType(generics = listOf(primitiveTypeStringNotNullable), primitive = primitiveTypeListNotNullable), withDefault = false)), supertypes = listOf()))), primitive = primitiveTypeMapNotNullable), withDefault = false)), extProperties = listOf(), isAbstract = false)

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntity", metadataHash = -2089661764)
        addMetadataHash(typeFqn = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectBuildClasspathEntity", metadataHash = 1679465766)
        addMetadataHash(typeFqn = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalModuleBuildClasspathEntity", metadataHash = 1440036197)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = -632855931)
        addMetadataHash(typeFqn = "com.intellij.openapi.externalSystem.settings.workspaceModel.ExternalProjectsBuildClasspathEntitySource", metadataHash = -872557435)
    }

}

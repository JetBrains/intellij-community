// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.configurationStore.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.ExtendableClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl: MetadataStorageBase() {
    override fun initializeMetadata() {
        val primitiveTypeIntNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "Int")
        val primitiveTypeStringNotNullable = ValueTypeMetadata.SimpleType.PrimitiveType(isNullable = false, type = "String")
        
        var typeMetadata: StorageTypeMetadata
        
        typeMetadata = FinalClassMetadata.ClassMetadata(fqName = "com.intellij.java.configurationStore.SampleDummyParentCustomModuleEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "internalSource", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsFileEntitySource", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$FileInDirectory", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "fileNameId", valueType = primitiveTypeIntNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "directory", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectLocation", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectFileEntitySource",
"com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "file", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$ExactFile", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "file", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectLocation", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = ExtendableClassMetadata.AbstractClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation", subclasses = listOf(FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$DirectoryBased", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectFilePath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectDir", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "ideaFolder", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrlString", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectConfigLocation")),
FinalClassMetadata.ClassMetadata(fqName = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$FileBased", properties = listOf(OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "projectFilePath", valueType = primitiveTypeStringNotNullable, withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "iprFile", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "iprFileParent", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = false, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "baseDirectoryUrlString", valueType = primitiveTypeStringNotNullable, withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectConfigLocation"))), supertypes = listOf())), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.JpsProjectFileEntitySource",
"com.intellij.platform.workspace.jps.JpsFileEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))), supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))), withDefault = false),
OwnPropertyMetadata(isComputable = false, isKey = false, isOpen = false, name = "virtualFileUrl", valueType = ValueTypeMetadata.SimpleType.CustomType(isNullable = true, typeMetadata = FinalClassMetadata.KnownClass(fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")), withDefault = false)), supertypes = listOf("com.intellij.platform.workspace.jps.CustomModuleEntitySource",
"com.intellij.platform.workspace.storage.DummyParentEntitySource",
"com.intellij.platform.workspace.storage.EntitySource"))
        
        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 348424994)
        addMetadataHash(typeFqn = "com.intellij.java.configurationStore.SampleDummyParentCustomModuleEntitySource", metadataHash = -1785529430)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsFileEntitySource", metadataHash = -1709137196)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource", metadataHash = -1063074881)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource", metadataHash = -182040200)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$ExactFile", metadataHash = 1636464627)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation", metadataHash = 153096835)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$DirectoryBased", metadataHash = -1619672141)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectConfigLocation\$FileBased", metadataHash = 656180261)
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.jps.JpsProjectFileEntitySource\$FileInDirectory", metadataHash = -292490736)
    }

}

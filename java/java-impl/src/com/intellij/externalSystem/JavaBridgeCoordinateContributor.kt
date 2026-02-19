// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.java.impl.dependencySubstitution.LibraryMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.ModuleMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.mavenCoordinates
import com.intellij.java.library.MavenCoordinates
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.workspace.jps.entities.modifyLibraryEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.annotations.ApiStatus

public object JavaBridgeCoordinateEntitySource : EntitySource

@ApiStatus.Internal
public fun IdeModifiableModelsProvider.setModuleCoordinates(module: Module, moduleData: ModuleData) {
  val moduleCoordinates = moduleData.publication?.toMavenCoordinates() ?: return
  val moduleEntity = module.findModuleEntity(actualStorageBuilder) ?: return
  actualStorageBuilder.modifyModuleEntity(moduleEntity) {
    mavenCoordinates = ModuleMavenCoordinateEntity(moduleCoordinates, JavaBridgeCoordinateEntitySource)
  }
}

@ApiStatus.Internal
public fun IdeModifiableModelsProvider.setLibraryCoordinates(library: Library, libraryData: LibraryData) {
  val libraryCoordinates = libraryData.toMavenCoordinates() ?: return
  val libraryEntity = library.findLibraryEntity(actualStorageBuilder) ?: return
  actualStorageBuilder.modifyLibraryEntity(libraryEntity) {
    mavenCoordinates = LibraryMavenCoordinateEntity(libraryCoordinates, JavaBridgeCoordinateEntitySource)
  }
}

@ApiStatus.Internal
public fun ProjectCoordinate.toMavenCoordinates(): MavenCoordinates? {
  val groupId = groupId ?: return null
  val artifactId = artifactId ?: return null
  val version = version ?: return null
  return MavenCoordinates(groupId, artifactId, version)
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.java.impl.dependencySubstitution.LibraryMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.ModuleMavenCoordinateEntity
import com.intellij.java.impl.dependencySubstitution.mavenCoordinates
import com.intellij.java.library.MavenCoordinates
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.service.project.ExternalSystemCoordinateContributor
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.libraries.Library
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionCoordinateContributor
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyLibraryEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.findLibraryEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun IdeModifiableModelsProvider.setModuleCoordinates(module: Module, moduleData: ModuleData) {
  val moduleCoordinates = moduleData.publication?.toMavenCoordinates() ?: return
  val moduleEntity = module.findModuleEntity(actualStorageBuilder) ?: return
  actualStorageBuilder.modifyModuleEntity(moduleEntity) {
    mavenCoordinates = ModuleMavenCoordinateEntity(moduleCoordinates, entitySource)
  }
}

@ApiStatus.Internal
fun IdeModifiableModelsProvider.setLibraryCoordinates(library: Library, libraryData: LibraryData) {
  val libraryCoordinates = libraryData.toMavenCoordinates() ?: return
  val libraryEntity = library.findLibraryEntity(actualStorageBuilder) ?: return
  actualStorageBuilder.modifyLibraryEntity(libraryEntity) {
    mavenCoordinates = LibraryMavenCoordinateEntity(libraryCoordinates, entitySource)
  }
}

private fun ProjectCoordinate.toMavenCoordinates(): MavenCoordinates? {
  val groupId = groupId ?: return null
  val artifactId = artifactId ?: return null
  val version = version ?: return null
  return MavenCoordinates(groupId, artifactId, version)
}

private class JavaBridgeCoordinateContributor : DependencySubstitutionCoordinateContributor {

  override fun findModuleCoordinate(module: ModuleEntity): MavenCoordinates? {
    if (module !is WorkspaceEntityBase) return null
    val moduleBridge = module.findModule(module.snapshot) ?: return null

    var result: ProjectCoordinate? = null
    ExternalSystemCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
      result = result ?: contributor.findModuleCoordinate(moduleBridge)
    }

    return result?.toMavenCoordinates()
  }

  override fun findLibraryCoordinate(library: LibraryEntity): MavenCoordinates? {
    if (library !is WorkspaceEntityBase) return null
    val libraryBridge = library.findLibraryBridge(library.snapshot) ?: return null

    var result: ProjectCoordinate? = null
    ExternalSystemCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
      result = result ?: contributor.findLibraryCoordinate(libraryBridge)
    }

    return result?.toMavenCoordinates()
  }
}
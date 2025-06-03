// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate
import com.intellij.openapi.externalSystem.service.project.ExternalSystemCoordinateContributor
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionCoordinateContributor
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.findModule

private class JavaBridgeCoordinateContributor : DependencySubstitutionCoordinateContributor {

  override fun findModuleCoordinate(module: ModuleEntity): ProjectCoordinate? {
    if (module !is WorkspaceEntityBase) return null
    val moduleBridge = module.findModule(module.snapshot) ?: return null

    var result: ProjectCoordinate? = null
    ExternalSystemCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
      result = result ?: contributor.findModuleCoordinate(moduleBridge)
    }

    return result
  }

  override fun findLibraryCoordinate(library: LibraryEntity): ProjectCoordinate? {
    if (library !is WorkspaceEntityBase) return null
    val libraryBridge = library.findLibraryBridge(library.snapshot) ?: return null

    var result: ProjectCoordinate? = null
    ExternalSystemCoordinateContributor.EP_NAME.forEachExtensionSafe { contributor ->
      result = result ?: contributor.findLibraryCoordinate(libraryBridge)
    }

    return result
  }
}
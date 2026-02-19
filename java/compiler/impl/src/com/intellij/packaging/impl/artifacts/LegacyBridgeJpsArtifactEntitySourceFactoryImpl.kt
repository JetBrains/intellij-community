// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.packaging.artifacts.LegacyBridgeJpsArtifactEntitySourceFactory
import com.intellij.platform.workspace.jps.JpsEntitySourceFactory
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation

class LegacyBridgeJpsArtifactEntitySourceFactoryImpl(val project: Project) : LegacyBridgeJpsArtifactEntitySourceFactory {
  override fun createEntitySourceForArtifact(externalSource: ProjectModelExternalSource?): EntitySource {
    val location = getJpsProjectConfigLocation(project) ?: return NonPersistentEntitySource
    val internalFile = JpsEntitySourceFactory.createJpsEntitySourceForArtifact(location)
    if (externalSource == null) return internalFile
    return JpsImportedEntitySource(internalFile, externalSource.id, project.isExternalStorageEnabled)
  }
}

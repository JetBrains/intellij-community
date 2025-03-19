// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.artifacts

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.platform.workspace.storage.EntitySource

/**
 * Factory to create entity sources for the Workspace Model [com.intellij.java.workspace.entities.ArtifactEntity], that is compatible with
 *   the JPS project model
 * Artifacts created with this entity source will work the same as the [Artifact], including creating files under .idea directory
 */
interface LegacyBridgeJpsArtifactEntitySourceFactory {
  fun createEntitySourceForArtifact(externalSource: ProjectModelExternalSource?): EntitySource

  companion object {
    fun getInstance(project: Project): LegacyBridgeJpsArtifactEntitySourceFactory = project.service()
  }
}

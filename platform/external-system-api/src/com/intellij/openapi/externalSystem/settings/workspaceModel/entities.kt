// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.settings.workspaceModel

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

object ExternalProjectsBuildClasspathEntitySource: EntitySource

interface ExternalProjectsBuildClasspathEntity : WorkspaceEntity {
  val projectsBuildClasspath: Map<String, ExternalProjectBuildClasspathEntity>
}

data class ExternalModuleBuildClasspathEntity(val path: String, val entries: List<String>)

data class ExternalProjectBuildClasspathEntity(val name: String,
                                               val projectBuildClasspath: List<String>,
                                               val moduleBuildClasspath: Map<String, ExternalModuleBuildClasspathEntity>)

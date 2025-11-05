// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface DependencySubstitutionEntity : WorkspaceEntity {

  @Parent
  val owner: ModuleEntity

  val library: LibraryId

  val module: ModuleId

  val scope: DependencyScope
}

@get:Internal
val ModuleEntity.substitutions: List<DependencySubstitutionEntity>
  by WorkspaceEntity.extension()
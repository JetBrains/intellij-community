// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.dependencySubstitution

import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionExtension
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionUtil.intersect
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities

private class MavenCoordinateDependencySubstitutionExtension : DependencySubstitutionExtension {

  override fun buildLibraryToModuleMap(storage: EntityStorage): Map<LibraryId, ModuleId> {
    val libraries = storage.entities<LibraryMavenCoordinateEntity>()
      .associate { it.coordinates to it.library.symbolicId }
    val modules = storage.entities<ModuleMavenCoordinateEntity>()
      .associate { it.coordinates to it.module.symbolicId }
    return libraries.intersect(modules)
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.externalSystem.impl.dependencySubstitution.DependencySubstitutionCoordinateContributor
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity

private class DependencySubstitutionMavenCoordinateContributor : DependencySubstitutionCoordinateContributor {

  override fun findModuleCoordinate(module: ModuleEntity): MavenCoordinates? {
    return module.mavenCoordinates?.coordinates
  }

  override fun findLibraryCoordinate(library: LibraryEntity): MavenCoordinates? {
    return library.mavenCoordinates?.coordinates
  }
}
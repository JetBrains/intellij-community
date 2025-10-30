// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Experimental
package com.intellij.java.impl.dependencySubstitution

import com.intellij.java.library.MavenCoordinates
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
public interface LibraryMavenCoordinateEntity : WorkspaceEntity {

  @Parent
  public val library: LibraryEntity

  public val coordinates: MavenCoordinates
}

public val LibraryEntity.mavenCoordinates: LibraryMavenCoordinateEntity?
  by WorkspaceEntity.extension()
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.java.library.LibraryWithMavenCoordinatesProperties
import com.intellij.java.library.MavenCoordinates
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor

public class ImportedLibraryProperties(override var mavenCoordinates: MavenCoordinates?) : LibraryProperties<ImportedLibraryProperties.MavenCoordinatesState>(), LibraryWithMavenCoordinatesProperties  {

  // required for serialization
  @Suppress("unused")
  public constructor() : this(null)
  override fun equals(other: Any?): Boolean {
    if (other != null && other is ImportedLibraryProperties) {
      return mavenCoordinates == other.mavenCoordinates
    }
    return false
  }

  override fun hashCode(): Int {
    return mavenCoordinates?.hashCode() ?: 42
  }

  override fun getState(): MavenCoordinatesState {
    val coords = mavenCoordinates
    return if (coords != null) {
      MavenCoordinatesState(coords)
    } else {
      MavenCoordinatesState()
    }
  }

  override fun loadState(state: MavenCoordinatesState) {
    this.mavenCoordinates = MavenCoordinates(state.groupId, state.artifactId, state.version, state.baseVersion, state.packaging, state.classifier)
  }

  public class MavenCoordinatesState() {
    public constructor(coordinates: MavenCoordinates): this() {
      groupId = coordinates.groupId
      artifactId = coordinates.artifactId
      version = coordinates.version
      baseVersion = coordinates.baseVersion
      packaging = coordinates.packaging
      classifier = coordinates.classifier
    }
    @Attribute
    public var groupId: String = ""
    @Attribute
    public var artifactId: String= ""
    @Attribute
    public var version: String = ""
    @Attribute
    public var baseVersion: String = ""
    @Attribute
    public var packaging: String = JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING
    @Attribute
    public var classifier: String? = null
  }
}
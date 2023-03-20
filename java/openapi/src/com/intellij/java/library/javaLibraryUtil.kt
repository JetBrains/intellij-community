// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaLibraryUtil")
package com.intellij.java.library

import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library

/**
 * Returns Maven coordinates of an artifact represented by the library, or `null` if this information isn't stored in the library configuration. 
 */
fun Library.getMavenCoordinates(): MavenCoordinates? {
  val properties = (this as? LibraryEx)?.properties
  if (properties is LibraryWithMavenCoordinatesProperties) {
    return properties.mavenCoordinates
  }

  /* this is a temporary solution until IJP-1410 is fixed; 
     it relies on the formats used by Maven importer (MavenArtifact::getLibraryName) and Gradle (org.jetbrains.plugins.gradle.DefaultExternalDependencyId.getPresentableName) */
  val name = name ?: return null
  val coordinatesString = name.substringAfter(": ")
  val parts = coordinatesString.split(':')
  if (parts.size < 3) return null
  return MavenCoordinates(parts[0], parts[1], parts.last())
}

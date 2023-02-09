// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library

/**
 * Base interface for implementations of [com.intellij.openapi.roots.libraries.LibraryProperties] which corresponding to libraries with
 * Maven coordinates.
 * 
 * For libraries which properties instance implements this interface, [com.intellij.openapi.roots.libraries.Library.getMavenCoordinates] 
 * function will return the Maven coordinates.
 */
interface LibraryWithMavenCoordinatesProperties {
  val mavenCoordinates: MavenCoordinates?
} 
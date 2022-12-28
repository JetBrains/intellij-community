// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library

import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor

/**
 * Describes coordinates of a Maven artifact.
 */
data class MavenCoordinates @JvmOverloads constructor(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val packaging: String = JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING,
  val classifier: String? = null
)
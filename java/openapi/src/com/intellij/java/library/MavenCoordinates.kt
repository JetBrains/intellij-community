// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.library

import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor

/**
 * Describes coordinates of a Maven artifact.
 */
data class MavenCoordinates @JvmOverloads constructor(
  val groupId: @NlsSafe String,
  val artifactId: @NlsSafe String,
  val version: @NlsSafe String,
  val packaging: @NonNls String = JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING,
  val classifier: @NonNls String? = null
)

fun Library.getMavenCoordinates(): MavenCoordinates? = JavaLibraryUtil.getMavenCoordinates(this)
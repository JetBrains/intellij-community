// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

interface ArtifactsServer {
  /**
   * Returns full URL which can be used to download an artifact.
   * @param relativePath path to an artifact from [org.jetbrains.intellij.build.BuildPaths.artifactDir] directory
   */
  fun urlToArtifact(context: BuildContext, relativePath: String): String?
}
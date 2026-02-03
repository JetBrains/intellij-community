// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.junit.Assert
import org.junit.Test

class BuildDependenciesDownloaderTest {
  @Test
  fun getUriForMavenArtifact() {
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      "https://my-host/path",
      "org.groupId",
      "artifactId",
      "1.1",
      "zip"
    )
    Assert.assertEquals("https://my-host/path/org/groupId/artifactId/1.1/artifactId-1.1.zip", uri.toString())
  }

  @Test
  fun getUriForMavenArtifact_classifier() {
    val uri = BuildDependenciesDownloader.getUriForMavenArtifact(
      "https://my-host/path",
      "org.groupId",
      "artifactId",
      "1.1",
      "bin",
      "zip"
    )
    Assert.assertEquals("https://my-host/path/org/groupId/artifactId/1.1/artifactId-1.1-bin.zip", uri.toString())
  }
}
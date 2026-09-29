// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CommunityDevDistGeneratorTest {
  @TempDir
  lateinit var dir: Path

  @Test
  fun `a run from the community root and a run from the monorepo root find one community root`() {
    val communityRoot = Files.createDirectories(dir.resolve("community"))
    Files.writeString(communityRoot.resolve(".community.root.marker"), "")
    val nested = Files.createDirectories(communityRoot.resolve("platform/build-scripts"))

    assertThat(findCommunityRoot(communityRoot)).isEqualTo(communityRoot)
    assertThat(findCommunityRoot(dir)).isEqualTo(communityRoot)
    assertThat(findCommunityRoot(nested)).isEqualTo(communityRoot)
  }

  @Test
  fun `a directory without a community root above it fails and names the start`() {
    val start = Files.createDirectories(dir.resolve("elsewhere"))

    assertThatThrownBy { findCommunityRoot(start) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(start.toString())
      .hasMessageContaining(".community.root.marker")
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// `community/platform/bazel-runfiles` has no test source, so this test lives in the build-scripts test module.
package org.jetbrains.intellij.build

import com.intellij.platform.bazel.runfiles.BazelRunfilesManifest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BazelRunfilesManifestTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun aDirectoryIsResolvedFromItsManifestEntries() {
    val manifestFile = tempDir.resolve("MANIFEST")
    val file = tempDir.resolve("execroot/a/native/libnative.so")
    Files.writeString(manifestFile, "a/native/libnative.so $file\n")

    assertThat(BazelRunfilesManifest(manifestFile.toString()).get("a/native"))
      .isEqualTo(tempDir.resolve("execroot/a/native").toString())
  }

  @Test
  fun aDirectoryDoesNotMatchASiblingWithTheSamePrefix() {
    val manifestFile = tempDir.resolve("MANIFEST")
    val file = tempDir.resolve("execroot/a/native-extra/x.jar")
    Files.writeString(manifestFile, "a/native-extra/x.jar $file\n")

    assertThat(BazelRunfilesManifest(manifestFile.toString()).get("a/native")).isEqualTo("a/native")
  }
}
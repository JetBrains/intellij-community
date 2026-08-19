// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class BazelBuildInputsTest {
  @Test
  fun `explicit input configuration follows the manifest property`(@TempDir tempDir: Path) {
    val propertyName = "intellij.build.bazel.inputs.manifest"
    val previousValue = System.getProperty(propertyName)
    try {
      System.clearProperty(propertyName)
      assertThat(BazelBuildInputs.isConfigured).isFalse()

      System.setProperty(propertyName, tempDir.resolve("inputs.manifest").toString())
      assertThat(BazelBuildInputs.isConfigured).isTrue()
    }
    finally {
      if (previousValue == null) {
        System.clearProperty(propertyName)
      }
      else {
        System.setProperty(propertyName, previousValue)
      }
    }
  }

  @Test
  fun `unused inputs retain sorted deduplicated Bazel exec paths`(@TempDir tempDir: Path) {
    val usedExecPath = "bazel-out/jvm-fastbuild/bin/platform/used.jar"
    val firstUnusedExecPath = "bazel-out/jvm-fastbuild/bin/plugins/a.jar"
    val secondUnusedExecPath = "bazel-out/jvm-fastbuild/bin/plugins/z.jar"
    val manifest = tempDir.resolve("inputs.manifest")
    Files.writeString(
      manifest,
      listOf(
        "@@community+//platform:used.jar\t$usedExecPath",
        "//plugins:z.jar\t$secondUnusedExecPath",
        "//plugins:a.jar\t$firstUnusedExecPath",
        "//plugins:a-alias.jar\t$firstUnusedExecPath",
      ).joinToString(separator = "\n", postfix = "\n"),
    )

    val resolver = ExplicitBazelInputResolver.load(manifest)

    assertThat(resolver.resolve("@community//platform:used.jar")).isEqualTo(Path.of(usedExecPath).toAbsolutePath().normalize())
    val unusedInputs = tempDir.resolve("unused-inputs")
    resolver.writeUnusedInputs(unusedInputs)
    assertThat(Files.readString(unusedInputs)).isEqualTo("$firstUnusedExecPath\n$secondUnusedExecPath\n")
  }

  @Test
  fun `absolute input paths are rejected`(@TempDir tempDir: Path) {
    val manifest = tempDir.resolve("inputs.manifest")
    val absoluteInput = tempDir.resolve("input.jar")
    Files.writeString(manifest, "//platform:input.jar\t$absoluteInput\n")

    assertThatThrownBy { ExplicitBazelInputResolver.load(manifest) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("must be relative to the execution root")
  }
}

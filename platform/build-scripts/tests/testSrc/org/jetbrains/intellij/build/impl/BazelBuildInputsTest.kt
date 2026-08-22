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
  fun `a repeated label collects its files in manifest order`(@TempDir tempDir: Path) {
    // How a multi-jar library states its jars: one line per file, all under the container's key. Order is the
    // container's `exports` order and must survive, because the packer resolves a duplicated entry to its first source.
    val first = "bazel-out/jvm-fastbuild/bin/lib/grazie-rule-engine.jar"
    val second = "bazel-out/jvm-fastbuild/bin/lib/jemoji.jar"
    val manifest = tempDir.resolve("inputs.manifest")
    Files.writeString(
      manifest,
      listOf(
        "@@lib+//:ai-grazie-rule-engine\t$first",
        "@@lib+//:ai-grazie-rule-engine\t$second",
      ).joinToString(separator = "\n", postfix = "\n"),
    )

    val resolver = ExplicitBazelInputResolver.load(manifest)
    val expected = listOf(first, second).map { Path.of(it).toAbsolutePath().normalize() }

    assertThat(resolver.resolveAll("@@lib+//:ai-grazie-rule-engine")).containsExactlyElementsOf(expected)
    // The apparent-repository alias names the same ordered list.
    assertThat(resolver.resolveAll("@lib//:ai-grazie-rule-engine")).containsExactlyElementsOf(expected)
    // `resolve` is the single-file contract, so a container is not silently truncated to its first jar.
    assertThatThrownBy { resolver.resolve("@lib//:ai-grazie-rule-engine") }.isInstanceOf(IllegalArgumentException::class.java)
    // Resolving the key marks every file it returned used, so an unread sibling jar is not reported as unused.
    val unusedInputs = tempDir.resolve("unused-inputs")
    resolver.writeUnusedInputs(unusedInputs)
    assertThat(Files.readString(unusedInputs)).isEmpty()
  }

  @Test
  fun `the same label may not repeat one exec path`(@TempDir tempDir: Path) {
    // The writer deduplicates first-wins, so a repeat means two producers disagreed about the same key.
    val manifest = tempDir.resolve("inputs.manifest")
    val execPath = "bazel-out/jvm-fastbuild/bin/lib/only.jar"
    Files.writeString(manifest, "@@lib+//:only\t$execPath\n@@lib+//:only\t$execPath\n")

    assertThatThrownBy { ExplicitBazelInputResolver.load(manifest) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Duplicate Bazel input")
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

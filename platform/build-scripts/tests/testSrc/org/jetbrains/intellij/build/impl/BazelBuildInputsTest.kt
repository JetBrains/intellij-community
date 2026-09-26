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
  fun `a container names the file a source read, and naming marks nothing used`(@TempDir tempDir: Path) {
    // The defect ADR 0006 rule 3 records: all 26 jars of `ant` carry the same label, so the label cannot say which jar
    // an output took. A name is not a read, so asking must not shrink the unused-input list either.
    val first = "bazel-out/jvm-fastbuild/bin/ant/lib/ant.jar"
    val second = "bazel-out/jvm-fastbuild/bin/ant/lib/ant-launcher.jar"
    val resolver = load(tempDir, "@@lib+//ant/lib:ant\t$first", "@@lib+//ant/lib:ant\t$second")

    assertThat(resolver.declaredFileNameOf("@@lib+//ant/lib:ant", absolute(second))).isEqualTo("ant-launcher.jar")
    assertThat(resolver.declaredFileNameOf("@@lib+//ant/lib:absent", absolute(second))).isNull()
    val unusedInputs = tempDir.resolve("unused-inputs")
    resolver.writeUnusedInputs(unusedInputs)
    // Written out rather than re-derived from the implementation's own sort: `ant-launcher.jar` sorts before `ant.jar`.
    assertThat(Files.readString(unusedInputs)).isEqualTo("$second\n$first\n")
  }

  @Test
  fun `a key that declares one file names no file`(@TempDir tempDir: Path) {
    // A single-file label already names its file. 1 058 of the 1 397 labelled sources of one dev build carry no file
    // name. A name there would state the same file twice and would grow every plan file for nothing.
    val only = "bazel-out/jvm-fastbuild/bin/platform/util/util.jar"
    val resolver = load(tempDir, "@@community+//platform/util:util.jar\t$only")

    assertThat(resolver.declaredFileNameOf("@@community+//platform/util:util.jar", absolute(only))).isNull()
  }

  @Test
  fun `two files of one key that share a name are told apart by the shortest unique path`(@TempDir tempDir: Path) {
    // A container groups jars from several directories, so a file name can repeat under one key. The value then widens
    // by one name at a time, and it stops as soon as it separates the two.
    val first = "bazel-out/jvm-fastbuild/bin/lib/a/kotlin-stdlib.jar"
    val second = "bazel-out/jvm-fastbuild/bin/lib/b/kotlin-stdlib.jar"
    val third = "bazel-out/jvm-fastbuild/bin/lib/b/annotations.jar"
    val key = "@@lib+//:kotlin"
    val resolver = load(tempDir, "$key\t$first", "$key\t$second", "$key\t$third")

    assertThat(resolver.declaredFileNameOf(key, absolute(first))).isEqualTo("a/kotlin-stdlib.jar")
    assertThat(resolver.declaredFileNameOf(key, absolute(second))).isEqualTo("b/kotlin-stdlib.jar")
    // A file name that repeats nowhere stays the plain name, even beside two names that do repeat.
    assertThat(resolver.declaredFileNameOf(key, absolute(third))).isEqualTo("annotations.jar")
  }

  @Test
  fun `a shadowed name widens as far inside the execution path as it has to`(@TempDir tempDir: Path) {
    // Two files of one key can shadow each other. Two widths then name both files and the third separates them, so the
    // value is three names long. It is still a trailing path of an execution path, which is what keeps it portable.
    val shadowed = "bazel-out/jvm-fastbuild/bin/a/b.jar"
    val shadowing = "bazel-out/jvm-fastbuild/bin/x/a/b.jar"
    val key = "@@lib+//:shadow"
    val resolver = load(tempDir, "$key\t$shadowed", "$key\t$shadowing")

    assertThat(resolver.declaredFileNameOf(key, absolute(shadowed))).isEqualTo("bin/a/b.jar")
    assertThat(resolver.declaredFileNameOf(key, absolute(shadowing))).isEqualTo("x/a/b.jar")
  }

  @Test
  fun `a name never widens past the execution path itself`(@TempDir tempDir: Path) {
    // The search stops at the whole execution path. Here every width of the shorter file names both files, so that file
    // gets no name. A wider name would come from above the execution root, which is a fact of this machine, and a
    // report that keys on labels must hold none.
    val shadowed = "a/b.jar"
    val shadowing = "x/a/b.jar"
    val key = "@@lib+//:root-shadow"
    val resolver = load(tempDir, "$key\t$shadowed", "$key\t$shadowing")

    assertThat(resolver.declaredFileNameOf(key, absolute(shadowed))).isNull()
    assertThat(resolver.declaredFileNameOf(key, absolute(shadowing))).isEqualTo("x/a/b.jar")
  }

  @Test
  fun `a file a key does not declare gets no name`(@TempDir tempDir: Path) {
    // Without this the search hands the file a sibling's name, because a width that names one declared file always
    // looks unique. A wrong name is worse than none: it says the source read a jar it never opened.
    val key = "@@lib+//:two"
    val resolver = load(tempDir, "$key\tbazel-out/bin/lib/a/x.jar", "$key\tbazel-out/bin/lib/b/y.jar")

    assertThat(resolver.declaredFileNameOf(key, absolute("bazel-out/bin/other/x.jar"))).isNull()
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

  private fun load(tempDir: Path, vararg lines: String): ExplicitBazelInputResolver {
    val manifest = tempDir.resolve("inputs.manifest")
    Files.writeString(manifest, lines.joinToString(separator = "\n", postfix = "\n"))
    return ExplicitBazelInputResolver.load(manifest)
  }

  private fun absolute(execPath: String): Path = Path.of(execPath).toAbsolutePath().normalize()
}

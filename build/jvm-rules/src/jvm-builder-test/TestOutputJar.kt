// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.bazel.jvm.worker.test

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.bazel.jvm.worker.core.output.createEmptyOutputSink
import org.jetbrains.kotlin.backend.common.output.OutputFile
import java.io.File

class TestOutputJar {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      testOutputJar()
    }
  }
}

private fun testOutputJar() {
  createEmptyOutputSink(withAbi = false).use { outputSink ->
    outputSink.registerKotlincOutput(listOf(
      outputFile("a/b/c/a"),
      outputFile("a/b/c/b"),
      outputFile("a/b/c/c/r"),
      outputFile("a/b/d/c/r"),
      outputFile("a/b/c/!special"),
      outputFile("a/b/c/!special/test"),
    ))

    assertThat(outputSink.findByPackage("a.b.c", recursive = false).map { it.first }.toList()).containsExactly(
      "a/b/c/!special",
      "a/b/c/a",
      "a/b/c/b",
    )

    assertThat(outputSink.findByPackage("a.b.c", recursive = true).map { it.first }.toList()).containsExactly(
      "a/b/c/!special",
      "a/b/c/!special/test",
      "a/b/c/a",
      "a/b/c/b",
      "a/b/c/c/r",
    )
  }
}

private fun OutputSink.findByPackage(packageName: String, recursive: Boolean): List<Pair<String, ByteArray>> {
  val result = ArrayList<Pair<String, ByteArray>>()
  findByPackage(packageName, recursive) { name, data, _, _ ->
    result.add(name to data)
  }
  return result
}

private fun outputFile(path: String): OutputFile {
  return object : OutputFile {
    override val relativePath = path

    override val sourceFiles: List<File>
      get() = emptyList()

    override fun asByteArray(): ByteArray = byteArrayOf()

    override fun asText(): String = asByteArray().decodeToString()
  }
}
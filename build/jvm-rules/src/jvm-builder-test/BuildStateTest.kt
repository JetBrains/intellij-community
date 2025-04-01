// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "TestOnlyProblems")

package org.jetbrains.bazel.jvm.worker.test

import kotlinx.coroutines.runBlocking
import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.util.toScatterMap
import org.jetbrains.bazel.jvm.worker.state.PathRelativizer
import org.jetbrains.bazel.jvm.worker.state.SourceDescriptor
import org.jetbrains.bazel.jvm.worker.state.loadBuildState
import org.jetbrains.bazel.jvm.worker.state.saveBuildState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.random.Random

internal object BuildStateTest {
  @OptIn(ExperimentalPathApi::class)
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    runBlocking {
      testSerialization()
    }
  }
}

private val relativizer = object : PathRelativizer {
  override fun toRelative(file: Path): String = file.invariantSeparatorsPathString

  override fun toAbsoluteFile(path: String): Path = Path.of(path)
}

internal suspend fun testSerialization() {
  val random = Random(42)
  val sourceDescriptors = Array(random.nextInt(100, 20_000)) {
    SourceDescriptor(
      sourceFile = Path.of("a/b/c/${java.lang.Long.toUnsignedString(random.nextLong(), 36)}.kt"),
      digest = random.nextBytes(32),
      outputs = Array(random.nextInt(0, 20)) {
        "a/b/${java.lang.Long.toUnsignedString(random.nextLong(), 36)}.class"
      },
      isChanged = true,
    )
  }
  sourceDescriptors.sortBy { it.sourceFile }

  val file = Files.createTempFile("test", ".arrow")
  RootAllocator(Long.MAX_VALUE).use { allocator ->
    try {
      saveBuildState(
        buildStateFile = file,
        list = sourceDescriptors,
        relativizer = relativizer,
        metadata = mapOf("version" to "1"),
        allocator = allocator,
      )

      val result = loadBuildState(
        buildStateFile = file,
        relativizer = relativizer,
        allocator = allocator,
        sourceFileToDigest = sourceDescriptors.toScatterMap { item, map -> map.put(item.sourceFile, item.digest) },
      )!!.map
      if (result.asMap().keys.sorted() != sourceDescriptors.map { it.sourceFile }) {
        throw AssertionError("Expected: $sourceDescriptors, actual: $result")
      }
      if (result.asMap().values.sortedBy { it.sourceFile } != sourceDescriptors.asList()) {
        throw AssertionError("Expected: $sourceDescriptors, actual: $result")
      }
    }
    finally {
      Files.deleteIfExists(file)
    }
  }
}
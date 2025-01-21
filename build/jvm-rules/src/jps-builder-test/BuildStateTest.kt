@file:Suppress("UnstableApiUsage", "TestOnlyProblems")

package org.jetbrains.bazel.jvm.jps.test

import org.apache.arrow.memory.RootAllocator
import org.jetbrains.bazel.jvm.jps.SourceDescriptor
import org.jetbrains.bazel.jvm.jps.state.loadBuildState
import org.jetbrains.bazel.jvm.jps.state.saveBuildState
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.random.Random

internal object BuildStateTest {
  @OptIn(ExperimentalPathApi::class)
  @JvmStatic
  fun main(startupArgs: Array<String>) {
    testSerialization()
  }
}

private val relativizer = object : PathTypeAwareRelativizer {
  override fun toRelative(path: String, type: RelativePathType): String = path

  override fun toRelative(path: Path, type: RelativePathType): String = path.invariantSeparatorsPathString

  override fun toAbsolute(path: String, type: RelativePathType): String = path

  override fun toAbsoluteFile(path: String, type: RelativePathType): Path = Path.of(path)
}

internal fun testSerialization() {
  val random = Random(42)
  val sourceDescriptors = Array<SourceDescriptor>(random.nextInt(100, 20_000)) {
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
        sourceFileToDigest = sourceDescriptors.associate { it.sourceFile to it.digest },
      )!!.map
      if (result.keys.sorted() != sourceDescriptors.map { it.sourceFile }) {
        throw AssertionError("Expected: $sourceDescriptors, actual: $result")
      }
      if (result.values.sortedBy { it.sourceFile } != sourceDescriptors.asList()) {
        throw AssertionError("Expected: $sourceDescriptors, actual: $result")
      }
    }
    finally {
      Files.deleteIfExists(file)
    }
  }
}
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import org.apache.arrow.memory.RootAllocator
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
  val sourceDescriptors = arrayOf(
    SourceDescriptor(
      sourceFile = Path.of("a/b/c.kt"),
      digest = random.nextBytes(32),
      outputs = listOf("a/b/c.class")
    ),
    SourceDescriptor(
      sourceFile = Path.of("a/b/d.kt"),
      digest = random.nextBytes(32),
      outputs = listOf("a/b/d.class")
    ),
  )

  val file = Files.createTempFile("test", ".arrow")
  RootAllocator(Long.MAX_VALUE).use { allocator ->
    try {
      saveBuildState(buildStateFile = file, list = sourceDescriptors, relativizer = relativizer, allocator = allocator)

      val result = loadBuildState(file, relativizer, allocator, null)
      if (result!!.keys.sorted() != sourceDescriptors.map { it.sourceFile }) {
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
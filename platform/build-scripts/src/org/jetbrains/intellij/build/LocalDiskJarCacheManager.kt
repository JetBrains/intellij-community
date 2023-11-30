// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build

import com.dynatrace.hash4j.hashing.Hashing
import java.nio.file.*
import java.security.MessageDigest
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

// `CREATE_NEW`: Ensure that we don't create a new file in a location if one already exists.
// This is important for the computation of distribution checksums,
// as we take the last modified time of the file into account.
private val TOUCH_OPTIONS = EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
const val UNMODIFIED_MARK_FILE_NAME: String = ".unmodified"

fun createMarkFile(file: Path): Boolean {
  try {
    Files.newByteChannel(file, TOUCH_OPTIONS)
    return true
  }
  catch (ignore: NoSuchFileException) {
    return false
  }
  catch (ignore: FileAlreadyExistsException) {
    return true
  }
}

internal fun createSourceAndCacheStrategyList(sources: List<Source>, classOutDirectory: Path): List<SourceAndCacheStrategy> {
  return sources
    .map { source ->
      when {
        source is DirSource -> {
          val dir = source.dir
          if (dir.startsWith(classOutDirectory)) {
            ModuleOutputSourceAndCacheStrategy(source = source, path = classOutDirectory.relativize(dir).toString())
          }
          else {
            throw UnsupportedOperationException("$source is not supported")
          }
        }
        source is InMemoryContentSource -> {
          InMemorySourceAndCacheStrategy(source)
        }
        source !is ZipSource -> {
          throw UnsupportedOperationException("$source is not supported")
        }
        !source.file.startsWith(MAVEN_REPO) -> {
          NonMavenJarSourceAndCacheStrategy(source)
        }
        else -> {
          MavenJarSourceAndCacheStrategy(source)
        }
      }
    }
    .sortedBy { it.path }
}

internal sealed interface SourceAndCacheStrategy {
  val source: Source
  val path: String

  fun getHash(): Long

  fun getSize(): Long

  fun updateDigest(digest: MessageDigest)
}

private class MavenJarSourceAndCacheStrategy(override val source: ZipSource) : SourceAndCacheStrategy {
  override val path = MAVEN_REPO.relativize(source.file).invariantSeparatorsPathString

  override fun getHash() = Hashing.komihash5_0().hashCharsToLong(path)

  override fun getSize(): Long = Files.size(source.file)

  override fun updateDigest(digest: MessageDigest) {
    // path includes version - that's enough
  }
}

private class NonMavenJarSourceAndCacheStrategy(override val source: ZipSource) : SourceAndCacheStrategy {
  private var hash: Long = 0

  override val path = source.file.toString()

  override fun getHash() = hash

  override fun getSize(): Long = Files.size(source.file)

  override fun updateDigest(digest: MessageDigest) {
    val fileContent = Files.readAllBytes(source.file)
    digest.update(fileContent)
    hash = Hashing.komihash5_0().hashBytesToLong(fileContent)
  }
}

private class ModuleOutputSourceAndCacheStrategy(override val source: DirSource, override val path: String) : SourceAndCacheStrategy {
  private var hash: Long = 0

  override fun getHash() = hash

  override fun getSize(): Long = 0

  override fun updateDigest(digest: MessageDigest) {
    hash = computeHashForModuleOutput(source.dir)
    digest.update(ByteArray(Long.SIZE_BYTES) { (hash shr (8 * it)).toByte() })
  }
}

private class InMemorySourceAndCacheStrategy(override val source: InMemoryContentSource) : SourceAndCacheStrategy {
  override val path: String
    get() = source.relativePath

  override fun getHash() = Hashing.komihash5_0().hashBytesToLong(source.data)

  override fun getSize(): Long = 0

  override fun updateDigest(digest: MessageDigest) {
    digest.update(source.data)
  }
}

private fun computeHashForModuleOutput(dir: Path): Long {
  val markFile = dir.resolve(UNMODIFIED_MARK_FILE_NAME)
  val lastModified = try {
    Files.getLastModifiedTime(markFile).toMillis()
  }
  catch (e: NoSuchFileException) {
    if (createMarkFile(markFile)) {
      Files.getLastModifiedTime(markFile).toMillis()
    }
    else {
      // module doesn't exist at all
      0
    }
  }
  return lastModified
}

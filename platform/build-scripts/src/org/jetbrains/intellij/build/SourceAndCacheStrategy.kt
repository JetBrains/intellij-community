// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import java.nio.file.*
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

// `CREATE_NEW`: Ensure that we don't create a new file in a location if one already exists.
// This is important for the computation of distribution checksums,
// as we take the last modified time of the file into account.
private val TOUCH_OPTIONS = EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
private const val UNMODIFIED_MARK_FILE_NAME: String = ".unmodified"

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

internal fun createSourceAndCacheStrategyList(sources: List<Source>, productionClassOutDir: Path): List<SourceAndCacheStrategy> {
  return sources
    .map { source ->
      when (source) {
        is DirSource -> {
          val dir = source.dir
          if (dir.startsWith(productionClassOutDir)) {
            ModuleOutputSourceAndCacheStrategy(source = source, name = productionClassOutDir.relativize(dir).toString())
          }
          else {
            throw UnsupportedOperationException("$source is not supported")
          }
        }
        is InMemoryContentSource -> InMemorySourceAndCacheStrategy(source)
        is FileSource -> FileSourceCacheStrategy(source)
        is ZipSource -> {
          if (!source.file.startsWith(MAVEN_REPO)) {
            NonMavenJarSourceAndCacheStrategy(source)
          }
          else {
            MavenJarSourceAndCacheStrategy(source)
          }
        }
        is LazySource -> LazySourceAndCacheStrategy(source)
      }
    }
    .sortedBy { it.name }
}

internal sealed interface SourceAndCacheStrategy {
  val source: Source
  val name: String

  fun getHash(): Long

  fun getSize(): Long

  fun updateDigest(digest: HashStream64)
}

private class MavenJarSourceAndCacheStrategy(override val source: ZipSource) : SourceAndCacheStrategy {
  override val name = MAVEN_REPO.relativize(source.file).invariantSeparatorsPathString

  override fun getHash() = Hashing.komihash5_0().hashCharsToLong(name)

  override fun getSize(): Long = Files.size(source.file)

  override fun updateDigest(digest: HashStream64) {
    // path includes version - that's enough
  }
}

private class LazySourceAndCacheStrategy(override val source: LazySource) : SourceAndCacheStrategy {
  override val name: String
    get() = source.name

  override fun getHash() = source.hash

  override fun getSize(): Long = 0

  override fun updateDigest(digest: HashStream64) {
    digest.putLong(source.hash)
  }
}

private class NonMavenJarSourceAndCacheStrategy(override val source: ZipSource) : SourceAndCacheStrategy {
  private var hash: Long = 0

  override val name = source.file.toString()

  override fun getHash() = hash

  override fun getSize(): Long = Files.size(source.file)

  override fun updateDigest(digest: HashStream64) {
    val fileContent = Files.readAllBytes(source.file)
    hash = Hashing.komihash5_0().hashBytesToLong(fileContent)
    digest.putLong(hash).putInt(fileContent.size)
  }
}

private class ModuleOutputSourceAndCacheStrategy(override val source: DirSource, override val name: String) : SourceAndCacheStrategy {
  private var hash: Long = 0

  override fun getHash() = hash

  override fun getSize(): Long = 0

  override fun updateDigest(digest: HashStream64) {
    hash = computeHashForModuleOutput(source)
    digest.putLong(hash)
  }
}

private class InMemorySourceAndCacheStrategy(override val source: InMemoryContentSource) : SourceAndCacheStrategy {
  private var hash: Long = 0

  override val name: String
    get() = source.relativePath

  override fun getHash() = hash

  override fun getSize(): Long = 0

  override fun updateDigest(digest: HashStream64) {
    hash = Hashing.komihash5_0().hashBytesToLong(source.data)
    digest.putLong(hash).putInt(source.data.size)
  }
}

private class FileSourceCacheStrategy(override val source: FileSource) : SourceAndCacheStrategy {
  private var hash: Long = source.hash

  override val name: String
    get() = source.relativePath

  override fun getHash() = hash

  override fun getSize(): Long = source.size.toLong()

  override fun updateDigest(digest: HashStream64) {
    digest.putLong(hash)
  }
}

internal fun computeHashForModuleOutput(source: DirSource): Long {
  val markFile = source.dir.resolve(UNMODIFIED_MARK_FILE_NAME)
  try {
    return Files.getLastModifiedTime(markFile).toMillis()
  }
  catch (e: NoSuchFileException) {
    if (createMarkFile(markFile)) {
      return Files.getLastModifiedTime(markFile).toMillis()
    }
    else {
      source.exist = false
      // module doesn't exist at all
      return 0
    }
  }
}
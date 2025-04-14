// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.worker.core

import org.jetbrains.jps.incremental.relativizer.PathRelativizer
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.io.File
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private const val BASE_ID_PREFIX = "#"

class BazelPathTypeAwareRelativizer(
  private val baseDir: Path,
  private val baseDirPrefix: String,
  private val baseDirParent: Path,
  private val parentOfBaseDirPrefix: String,
) : PathTypeAwareRelativizer {
  @JvmField val sourceRelativizer = object : org.jetbrains.bazel.jvm.worker.state.PathRelativizer {
    override fun toRelative(file: Path): String {
      return sourcePathToRelative(file.invariantSeparatorsPathString)
    }

    override fun toAbsoluteFile(path: String): Path = sourceRelativePathToSourceFile(path)
  }

  private fun sourcePathToRelative(p: String): String {
    return when {
      p.startsWith(baseDirPrefix) -> p.substring(baseDirPrefix.length)
      p.startsWith(parentOfBaseDirPrefix) -> "../" + p.substring(parentOfBaseDirPrefix.length)
      else -> error("Unexpected path: $p (baseDirPrefix=$baseDirPrefix, parentOfBaseDirPrefix=$parentOfBaseDirPrefix)")
    }
  }

  private fun sourceRelativePathToSourceFile(path: String): Path {
    return if (path.startsWith("../")) baseDirParent.resolve(path.substring(3)) else baseDir.resolve(path)
  }

  override fun toRelative(path: String, type: RelativePathType): String {
    val p = path.replace(File.separatorChar, '/')
    when (type) {
      RelativePathType.SOURCE -> return sourcePathToRelative(p)
      RelativePathType.OUTPUT -> throw IllegalStateException("OUTPUT must be registered via raw API")
    }
  }

  override fun toRelative(path: Path, type: RelativePathType): String {
    return toRelative(path.invariantSeparatorsPathString, type)
  }

  override fun toAbsoluteFile(path: String, type: RelativePathType): Path {
    return when (type) {
      RelativePathType.SOURCE -> sourceRelativePathToSourceFile(path)
      RelativePathType.OUTPUT -> Path.of(path)
    }
  }

  override fun toAbsolute(path: String, type: RelativePathType): String {
    return when (type) {
      RelativePathType.SOURCE -> baseDirPrefix + path
      RelativePathType.OUTPUT -> path
    }
  }
}

fun createPathRelativizer(baseDir: Path): PathRelativizerService {
  val baseDirPrefix = "${baseDir.invariantSeparatorsPathString}/"
  // Bazel may use paths with `../`
  val baseDirParent = baseDir.parent
  val parentOfBaseDirPrefix = "${baseDirParent.invariantSeparatorsPathString}/"

  return PathRelativizerService(arrayOf(object : PathRelativizer {
    override fun toRelativePath(path: String): String? {
      return when {
        path.startsWith(parentOfBaseDirPrefix) -> BASE_ID_PREFIX + "../" + path.substring(parentOfBaseDirPrefix.length)
        path.startsWith(baseDirPrefix) -> BASE_ID_PREFIX + path.substring(baseDirPrefix.length)
        else -> null
      }
    }

    override fun toAbsolutePath(path: String): String? {
      return when {
        path.startsWith(BASE_ID_PREFIX) -> baseDirPrefix + path.substring(BASE_ID_PREFIX.length)
        else -> null
      }
    }
  }), BazelPathTypeAwareRelativizer(
    baseDir = baseDir,
    baseDirPrefix = baseDirPrefix,
    baseDirParent = baseDirParent,
    parentOfBaseDirPrefix = parentOfBaseDirPrefix,
  ))
}

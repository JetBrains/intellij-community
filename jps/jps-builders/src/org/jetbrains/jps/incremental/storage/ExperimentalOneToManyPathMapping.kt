// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.incremental.storage.dataTypes.stringTo128BitHash
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Internal
enum class RelativePathType {
  SOURCE,
  OUTPUT,
}

@TestOnly
@Internal
object TestPathTypeAwareRelativizer : PathTypeAwareRelativizer {
  override fun toRelative(path: String, type: RelativePathType): String {
    return FileUtilRt.toSystemIndependentName(path)
  }

  override fun toRelative(path: Path, type: RelativePathType): String {
    return path.invariantSeparatorsPathString
  }

  override fun toAbsolute(path: String, type: RelativePathType): String {
    return FileUtilRt.toSystemIndependentName(path)
  }
}

@Internal
interface PathTypeAwareRelativizer {
  fun toRelative(path: String, type: RelativePathType): String

  fun toRelative(path: Path, type: RelativePathType): String

  fun toAbsolute(path: String, type: RelativePathType): String
}

@Internal
open class ExperimentalOneToManyPathMapping(
  @JvmField val mapHandle: MapHandle<LongArray, Array<String>>,
  @JvmField internal val relativizer: PathTypeAwareRelativizer,
  private val valueOffset: Int = 0,
  private val keyKind: RelativePathType,
  private val valueKind: RelativePathType,
) : OneToManyPathMapping {
  fun getKey(path: String): LongArray = stringTo128BitHash(relativizer.toRelative(path, keyKind))

  @Suppress("ReplaceGetOrSet")
  final override fun getOutputs(path: String): List<String>? {
    val key = getKey(path)
    val list = mapHandle.map.get(key) ?: return null
    return Array<String>(list.size - valueOffset) { relativizer.toAbsolute(list.get(it + valueOffset), valueKind) }.asList()
  }

  fun normalizeOutputPaths(outPaths: List<String>, relativeSourcePath: String?): Array<String>? {
    return when {
      outPaths.isEmpty() -> null
      relativeSourcePath != null -> {
        Array(outPaths.size + 1) {
          if (it == 0) relativeSourcePath else relativizer.toRelative(outPaths.get(it - 1), valueKind)
        }
      }
      else -> {
        Array(outPaths.size) {
          relativizer.toRelative(outPaths.get(it), valueKind)
        }
      }
    }
  }

  override fun setOutputs(path: String, outPaths: List<String>) {
    val relativeSourcePath = relativizer.toRelative(path, keyKind)
    val key = stringTo128BitHash(relativeSourcePath)
    val normalizeOutputPaths = normalizeOutputPaths(outPaths, null)
    if (normalizeOutputPaths == null) {
      mapHandle.map.remove(key)
    }
    else {
      mapHandle.map.put(key, normalizeOutputPaths)
    }
  }

  final override fun remove(path: String) {
    mapHandle.map.remove(getKey(path))
  }
}
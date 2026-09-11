// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.jarCache

import com.dynatrace.hash4j.hashing.HashStream64
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.ZipSource
import java.nio.file.Path

interface SourceBuilder {
  fun updateDigest(digest: HashStream64)

  fun produce(targetFile: Path)

  fun consumeInfo(source: Source, size: Int, hash: Long)
}

sealed interface JarCacheManager {
  /** Writes the jar for [sources] into [targetFile], from the cache when it holds a matching entry. */
  fun computeIfAbsent(
    sources: Collection<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  )

  fun cleanup()
}

internal data object NonCachingJarCacheManager : JarCacheManager {
  override fun computeIfAbsent(
    sources: Collection<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  ) {
    producer.produce(targetFile)
  }

  override fun cleanup() {
  }
}

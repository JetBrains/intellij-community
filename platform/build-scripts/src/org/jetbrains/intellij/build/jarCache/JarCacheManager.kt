// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.jarCache

import com.dynatrace.hash4j.hashing.HashStream64
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.Source
import org.jetbrains.intellij.build.ZipSource
import java.nio.file.Path

internal interface SourceBuilder {
  val useCacheAsTargetFile: Boolean

  // one module (source) can be included in different plugins - cache per plugin
  fun updateDigest(digest: HashStream64)

  suspend fun produce(targetFile: Path)

  fun consumeInfo(source: Source, size: Int, hash: Long)
}

internal sealed interface JarCacheManager {
  suspend fun computeIfAbsent(
    sources: Collection<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  ): Path

  suspend fun cleanup()
}

internal data object NonCachingJarCacheManager : JarCacheManager {
  override suspend fun computeIfAbsent(
    sources: Collection<Source>,
    targetFile: Path,
    nativeFiles: MutableMap<ZipSource, List<String>>?,
    span: Span,
    producer: SourceBuilder,
  ): Path {
    producer.produce(targetFile)
    return targetFile
  }

  override suspend fun cleanup() {
  }
}
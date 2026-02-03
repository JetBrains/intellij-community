// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
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
  override fun toRelative(path: String, type: RelativePathType): String = FileUtilRt.toSystemIndependentName(path)

  override fun toRelative(path: Path, type: RelativePathType): String = path.invariantSeparatorsPathString

  override fun toAbsolute(path: String, type: RelativePathType): String = FileUtilRt.toSystemIndependentName(path)

  override fun toAbsoluteFile(path: String, type: RelativePathType): Path = Path.of(path)
}

@Internal
interface PathTypeAwareRelativizer {
  fun toRelative(path: String, type: RelativePathType): String

  fun toRelative(path: Path, type: RelativePathType): String

  fun toAbsolute(path: String, type: RelativePathType): String

  fun toAbsoluteFile(path: String, type: RelativePathType): Path
}
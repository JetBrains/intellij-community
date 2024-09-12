// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil

internal open class CommonPathRelativizer @JvmOverloads constructor(
  private val basePath: String,
  private val identifier: String,
  private val isCaseSensitive: Boolean = SystemInfoRt.isFileSystemCaseSensitive,
) : PathRelativizer {
  override fun toRelativePath(path: String): String? {
    return if (FileUtil.startsWith(path, basePath, isCaseSensitive)) identifier + path.substring(basePath.length) else null
  }

  override fun toAbsolutePath(path: String): String? {
    return if (path.startsWith(identifier)) basePath + path.substring(identifier.length) else null
  }
}
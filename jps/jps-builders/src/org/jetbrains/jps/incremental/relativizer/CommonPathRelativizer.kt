// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil

internal open class CommonPathRelativizer @JvmOverloads constructor(
  private val basePath: String?, private val identifier: String,
  /**
   * can be null when basePath is null, or it is impossible to detect the sensitivity of the file system
   */
  private val isCaseSensitive: Boolean? = null
) : PathRelativizer {
  override fun toRelativePath(path: String): String? {
    if (basePath == null ||
        !(if (isCaseSensitive == null)
          FileUtil.startsWith(path, basePath, SystemInfoRt.isFileSystemCaseSensitive)
        else
          FileUtil.startsWith(path, basePath, isCaseSensitive))
    ) {
      return null
    }
    return identifier + path.substring(basePath.length)
  }

  override fun toAbsolutePath(path: String): String? {
    if (basePath == null || !path.startsWith(identifier)) {
      return null
    }
    else {
      return basePath + path.substring(identifier.length)
    }
  }
}

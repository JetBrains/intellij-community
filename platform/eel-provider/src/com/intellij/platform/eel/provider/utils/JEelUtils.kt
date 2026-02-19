// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.toEelApiBlocking
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * A collection of utility methods for Java.
 */
@ApiStatus.Internal
object JEelUtils {

  @JvmStatic
  fun getEelApi(eelPath: EelPath): EelApi {
    return eelPath.descriptor.toEelApiBlocking()
  }

  @JvmStatic
  fun toEelPath(path: Path): EelPath? = runCatching { path.asEelPath() }.getOrNull()

}
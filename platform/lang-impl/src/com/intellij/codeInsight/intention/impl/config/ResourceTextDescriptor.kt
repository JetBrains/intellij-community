// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config

import com.intellij.l10n.LocalizationUtil
import java.io.IOException

internal data class ResourceTextDescriptor(
  private val loader: ClassLoader?,
  private val resourcePath: String,
) : TextDescriptor {
  @Throws(IOException::class)
  override fun getText(): String {
    val stream = LocalizationUtil.getResourceAsStream(loader, resourcePath)
                 ?: throw IOException("Resource not found: $resourcePath; loader: $loader")
    return stream.use { it.readAllBytes().decodeToString() }
  }

  override fun getFileName(): String {
    return resourcePath.substring(resourcePath.lastIndexOf('/') + 1).removeSuffix(BeforeAfterActionMetaData.EXAMPLE_USAGE_URL_SUFFIX)
  }
}
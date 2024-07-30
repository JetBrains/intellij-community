// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config

import com.intellij.l10n.LocalizationUtil.getResourceAsStream
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ResourceUtil
import java.io.IOException

internal data class ResourceTextDescriptor(
  private val loader: ClassLoader,
  private val resourcePath: String,
) : TextDescriptor {
  @Throws(IOException::class)
  override fun getText(): String {
    val inputStream = getResourceAsStream(loader, resourcePath)
    if (inputStream != null) {
      try {
        inputStream.use {
          return ResourceUtil.loadText(inputStream)
        }
      }
      catch (e: IOException) {
        thisLogger().error("Cannot find localized resource: $resourcePath", e)
      }
    }

    val stream = loader.getResourceAsStream(resourcePath) ?: throw IOException("Resource not found: $resourcePath; loader: $loader")
    return ResourceUtil.loadText(stream)
  }

  override fun getFileName(): String {
    return resourcePath.substring(resourcePath.lastIndexOf('/') + 1).removeSuffix(BeforeAfterActionMetaData.EXAMPLE_USAGE_URL_SUFFIX)
  }
}
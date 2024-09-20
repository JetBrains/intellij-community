// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.external.client

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments

class NativeServerException(
  cause: Throwable,
  private val attachments: Array<Attachment> = emptyArray(),
) : Exception(cause), ExceptionWithAttachments {
  override fun getAttachments(): Array<Attachment> = attachments
}

sealed class DownloadManagerException(cause: Throwable) : RuntimeException(cause)
sealed class UnsupportedPlatformException(message: String) : DownloadManagerException(IllegalStateException(message))

class UnsupportedOSException(osName: String) : UnsupportedPlatformException("Unsupported OS: $osName")
class UnsupportedArchitectureException(architecture: String) : UnsupportedPlatformException("Unsupported architecture: $architecture")

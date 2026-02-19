// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import kotlinx.serialization.Serializable

/**
 * Represents [com.intellij.modcommand.ModCreateFile].
 */
@Serializable
data class RpcCreateFile(
  val parentPath: String,
  val fileName: String,
  val isDirectory: Boolean,
  val content: FileContent,
) : RpcModCommand {
  @Serializable
  sealed interface FileContent {
    @Serializable
    data object Empty : FileContent

    @Serializable
    data class Text(val text: String) : FileContent

    @Serializable
    data class Binary(val bytes: ByteArray) : FileContent {
      override fun equals(other: Any?): Boolean = other is Binary && bytes.contentEquals(other.bytes)
      override fun hashCode(): Int = bytes.contentHashCode()
    }
  }
}

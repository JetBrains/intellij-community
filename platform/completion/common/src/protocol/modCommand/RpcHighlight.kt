// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import kotlinx.serialization.Serializable

/**
 * Represents [com.intellij.modcommand.ModHighlight].
 */
@Serializable
data class RpcHighlight(
  val filePath: String,
  val highlights: List<HighlightInfo>,
) : RpcModCommand {
  @Serializable
  data class HighlightInfo(
    val startOffset: Int,
    val endOffset: Int,
    val attributesKeyExternalName: String,
    val hideByTextChange: Boolean,
  )
}

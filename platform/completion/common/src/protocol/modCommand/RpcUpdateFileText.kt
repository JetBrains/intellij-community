// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import kotlinx.serialization.Serializable

/**
 * Represents [com.intellij.modcommand.ModUpdateFileText].
 */
@Serializable
data class RpcUpdateFileText(
  val filePath: String,
  val oldText: String,
  val newText: String,
  val updatedRanges: List<Fragment>,
) : RpcModCommand {

  @Serializable
  data class Fragment(
    val offset: Int,
    val oldLength: Int,
    val newLength: Int,
  )
}

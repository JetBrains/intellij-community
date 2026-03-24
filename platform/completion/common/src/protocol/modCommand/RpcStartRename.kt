// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import kotlinx.serialization.Serializable

/**
 * Represents [com.intellij.modcommand.ModStartRename].
 */
@Serializable
data class RpcStartRename(
  val filePath: String,
  val range: RpcTextRange,
  val nameIdentifierRange: RpcTextRange?,
  val nameSuggestions: List<String>,
) : RpcModCommand

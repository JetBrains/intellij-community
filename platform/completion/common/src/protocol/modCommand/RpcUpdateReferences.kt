// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol.modCommand

import kotlinx.serialization.Serializable

/**
 * Represents [com.intellij.modcommand.ModUpdateReferences].
 */
@Serializable
data class RpcUpdateReferences(
  val filePath: String,
  val oldText: String,
  val oldRange: RpcTextRange,
  val newRange: RpcTextRange,
) : RpcModCommand

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import kotlinx.serialization.Serializable

/**
 * An event about updating the current prefix.
 * The user can either append a new character or remove the last one.
 */
@Serializable
sealed interface RpcPrefixUpdate {

  /** The last character of the prefix is removed. */
  @Serializable
  object Truncate : RpcPrefixUpdate

  /** A new character is appended to the prefix. */
  @Serializable
  class Append(val char: Char) : RpcPrefixUpdate {
    override fun toString(): String = buildToString("Append") {
      field("char", char)
    }
  }
}
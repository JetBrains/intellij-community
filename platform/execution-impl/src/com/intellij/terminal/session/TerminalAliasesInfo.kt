// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import com.intellij.openapi.util.Key
import kotlinx.serialization.Serializable

@Serializable
data class TerminalAliasesInfo(
  val aliases: Map<String, String>,
)

class TerminalAliasesStorage {

  private var aliasesInfo: TerminalAliasesInfo? = null

  fun setAliasesInfo(newAliasesInfo: TerminalAliasesInfo) {
    if (this.aliasesInfo == null) {
      this.aliasesInfo = newAliasesInfo
    }
  }

  fun getAliasesInfo(): TerminalAliasesInfo {
    return aliasesInfo
           ?: throw IllegalStateException("Aliases have not been set yet.")
  }

  companion object {
    val KEY: Key<TerminalAliasesStorage> = Key.create("TerminalAliasesStorage")
  }
}
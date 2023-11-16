// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import kotlinx.coroutines.CoroutineScope

@Deprecated("Use IjentChildProcess directly")
class AutoClosingIjentChildProcess private constructor(
  private val delegate: IjentChildProcess,
) : IjentChildProcess by delegate {
  companion object {
    @Deprecated("Use IjentChildProcess directly")
    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun create(parentCoroutineScope: CoroutineScope, delegate: IjentChildProcess): AutoClosingIjentChildProcess =
      AutoClosingIjentChildProcess(delegate)
  }

  override fun toString(): String = "${javaClass.simpleName}($delegate)"
}
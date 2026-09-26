// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.ijent.IjentScope
import kotlinx.coroutines.CompletableDeferred

data class IjentTcpSessionMediator(
  override val ijentProcessScope: IjentScope,
  override val processExit: SafeDeferred<Unit>,
  val remotePid: CompletableDeferred<Long> = CompletableDeferred(),
) : IjentSessionMediator
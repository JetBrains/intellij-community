// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

data class IjentTcpSessionMediator(
  override val ijentProcessScope: CoroutineScope,
  override val processExit: Deferred<Unit>,
) : IjentSessionMediator
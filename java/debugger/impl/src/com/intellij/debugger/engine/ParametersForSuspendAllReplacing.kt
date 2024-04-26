// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import java.util.function.Function

internal data class ParametersForSuspendAllReplacing(
  val threadSuspendContext: SuspendContextImpl,
  val performOnSuspendAll: Function<SuspendContextImpl, Boolean>
)

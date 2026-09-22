// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
object LastKnownDaemonVersion {
  val version: AtomicReference<String> = AtomicReference("unknown")
}
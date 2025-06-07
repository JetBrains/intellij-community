// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface FreezeNotifier {
  fun notifyFreeze(event: LogMessage, currentDumps: Collection<ThreadDump>, reportDir: Path, durationMs: Long)
}

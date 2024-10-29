// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.tracing

import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

interface DynamicTraceRecording : CoroutineContext.Element {
  companion object : CoroutineContext.Key<DynamicTraceRecording>

  override val key: CoroutineContext.Key<*> get() = DynamicTraceRecording

  suspend fun start(output: Path)
  suspend fun stop(): Path?
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import fleet.reporting.shared.tracing.span
import kotlinx.coroutines.CompletableJob
import kotlin.coroutines.CoroutineContext

class StartupBarrier(val job: CompletableJob): CoroutineContext.Element {
  companion object: CoroutineContext.Key<StartupBarrier>
  override val key: CoroutineContext.Key<*> get() = StartupBarrier
  suspend fun wait() { job.join() }
  fun release() { span("startup activity released") { job.complete() } }
}
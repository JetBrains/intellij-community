// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SpanKt")
@file:JvmMultifileClass

package fleet.tracing.runtime

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
data class SpanInfo(
  val name: String,
  val isScope: Boolean,
  val job: Any,
  val map: Map<String, String>,
  val startTimestampNano: Long?,
  val cause: Span?,
)

interface Span {
  object Noop : CompletableSpan {
    override val job: Any = Any()

    override fun startChild(childInfo: SpanInfo): CompletableSpan {
      return Noop
    }

    override fun complete(status: SpanStatus, endTimestampNano: Long?) {
    }
  }

  val job: Any
  fun startChild(childInfo: SpanInfo): CompletableSpan
}

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
sealed class SpanStatus {
  data object Success : SpanStatus()
  data object Cancelled : SpanStatus()
  data class Failed(val x: Throwable) : SpanStatus()
}

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
interface CompletableSpan : Span {
  fun complete(status: SpanStatus, endTimestampNano: Long?)
}

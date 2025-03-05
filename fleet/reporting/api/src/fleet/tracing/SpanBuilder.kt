// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.tracing

import fleet.tracing.runtime.Span
import fleet.tracing.runtime.SpanInfo

class SpanInfoBuilder(
  val name: String,
  val job: Any,
  private val isScope: Boolean,
) {
  private val map = HashMap<String, String>()
  var cause: Span? = null
  var startTimestampNano: Long? = null

  fun set(key: String, value: String) {
    map[key] = value
  }

  internal fun build(): SpanInfo =
    SpanInfo(
      name = name,
      job = job,
      map = map,
      isScope = isScope,
      startTimestampNano = startTimestampNano,
      cause = cause)
}

internal inline fun spanInfo(name: String, job: Any, isScope: Boolean, builder: SpanInfoBuilder.() -> Unit = {}): SpanInfo {
  return SpanInfoBuilder(name, job, isScope).apply(builder).build()
}

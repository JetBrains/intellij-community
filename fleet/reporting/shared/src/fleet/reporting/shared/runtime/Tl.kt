// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.reporting.shared.runtime

import fleet.tracing.runtime.Span
import fleet.multiplatform.shims.ThreadLocal

val currentSpanThreadLocal: ThreadLocal<Span?> = ThreadLocal()

val currentSpan: Span get() = currentSpanThreadLocal.get() ?: Span.Noop
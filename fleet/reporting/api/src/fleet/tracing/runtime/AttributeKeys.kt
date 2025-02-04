// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SpanKt")
@file:JvmMultifileClass

package fleet.tracing.runtime

import io.opentelemetry.api.common.AttributeKey

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
val THREAD_ID_KEY: AttributeKey<Long> = AttributeKey.longKey("threadId")

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.reporting.opentelemetry"])
val SCOPE_KEY: AttributeKey<Boolean> = AttributeKey.booleanKey("isScope")

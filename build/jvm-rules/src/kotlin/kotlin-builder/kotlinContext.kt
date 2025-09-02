// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.kotlin

import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.kotlinCompileContextKey
import org.jetbrains.kotlin.jps.build.testingContext
import kotlin.system.measureTimeMillis

internal fun getKotlinCompileContext(context: CompileContext): KotlinCompileContext = context.getUserData(kotlinCompileContextKey)!!

internal fun ensureKotlinContextInitialized(context: CompileContext, span: Span): KotlinCompileContext {
  context.getUserData(kotlinCompileContextKey)?.let {
    return it
  }

  synchronized(context) {
    return context.getUserData(kotlinCompileContextKey) ?: initializeKotlinContext(context, span)
  }
}

internal fun ensureKotlinContextDisposed(context: CompileContext) {
  if (context.getUserData(kotlinCompileContextKey) == null) {
    return
  }

  synchronized(context) {
    val kotlinCompileContext = context.getUserData(kotlinCompileContextKey) ?: return
    kotlinCompileContext.dispose()
    context.putUserData(kotlinCompileContextKey, null)
  }
}

private fun initializeKotlinContext(context: CompileContext, span: Span): KotlinCompileContext {
  lateinit var kotlinContext: KotlinCompileContext
  val time = measureTimeMillis {
    kotlinContext = KotlinCompileContext(context)

    context.putUserData(kotlinCompileContextKey, kotlinContext)
    context.testingContext?.kotlinCompileContext = kotlinContext
    kotlinContext.cleanupCaches()
    kotlinContext.reportUnsupportedTargets()
  }

  span.addEvent("total Kotlin global compile context initialization time: $time ms")
  return kotlinContext
}
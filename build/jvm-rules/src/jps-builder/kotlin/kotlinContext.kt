// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.kotlin

import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.kotlinCompileContextKey
import org.jetbrains.kotlin.jps.build.testingContext
import kotlin.system.measureTimeMillis

internal class KotlinContextHelper {
  /**
   * Ensure Kotlin Context initialized.
   * Kotlin Context should be initialized only when required (before the first kotlin chunk build).
   */
  fun ensureKotlinContextInitialized(context: CompileContext, span: Span): KotlinCompileContext {
    context.getUserData(kotlinCompileContextKey)?.let {
      return it
    }

    // don't synchronize on context, since it is chunk local only
    synchronized(kotlinCompileContextKey) {
      context.getUserData(kotlinCompileContextKey)?.let {
        return it
      }
      return initializeKotlinContext(context, span)
    }
  }

  private fun initializeKotlinContext(context: CompileContext, span: Span): KotlinCompileContext {
    lateinit var kotlinContext: KotlinCompileContext

    val time = measureTimeMillis {
      kotlinContext = KotlinCompileContext(context)

      context.putUserData(kotlinCompileContextKey, kotlinContext)
      context.testingContext?.kotlinCompileContext = kotlinContext

      if (kotlinContext.shouldCheckCacheVersions && kotlinContext.hasKotlin()) {
        kotlinContext.checkCacheVersions()
      }

      kotlinContext.cleanupCaches()
      kotlinContext.reportUnsupportedTargets()
    }

    span.addEvent("total Kotlin global compile context initialization time: $time ms")

    return kotlinContext
  }
}
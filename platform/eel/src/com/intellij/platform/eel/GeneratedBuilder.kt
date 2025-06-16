// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus

/**
 * `com.intellij.platform.eel.codegen.BuildersGenerator` generates builders for all methods
 * that have a single argument with this annotation.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
@ApiStatus.Internal
annotation class GeneratedBuilder {
  /**
   * The only purpose of this annotation is to help to find generated builders via "Find Usages".
   */
  annotation class Result
}

/**
 * A basic interface for methods with optional arguments.
 *
 * The code
 * ```kotlin
 * val result = someApi.someMethod().arg1(1).arg2(2).eelIt()
 * ```
 * is identical to something like that:
 * ```kotlin
 * val builder = SomeeApi.SomeMethodArgs.Builder()
 * builder.arg1(1)
 * builder.arg2(2)
 * val result = someApi.someMethod(builder.build())
 * ```
 */
@ApiStatus.Internal
interface OwnedBuilder<T> {
  suspend fun eelIt(): T
}
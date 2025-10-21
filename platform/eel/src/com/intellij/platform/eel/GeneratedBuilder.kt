// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

/**
 * `com.intellij.platform.eel.codegen.BuildersGeneratorTest` generates builders for all methods
 * that have a single argument with this annotation.
 *
 * @property type The class type associated with the generated builder.
 * It must be a subtype of the annotated argument's type.
 * Default value of the type property is `GeneratedBuilder::class` which means that the annotated argument's type will be used.
 *
 * Examples:
 * ```kotlin
 * // Generates a builder for `ExecuteProcessWindowsOptions`
 * fun spawnProcess(@GeneratedBuilder(ExecuteProcessWindowsOptions::class) generatedBuilder: ExecuteProcessOptions)
 * ```
 * `````kotlin
 * // Generates a builder for `ExecuteProcessOptions`
 * fun spawnProcess(@GeneratedBuilder() generatedBuilder: ExecuteProcessOptions)
 * ```
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
@ApiStatus.Internal
annotation class GeneratedBuilder(val type: KClass<*> = GeneratedBuilder::class) {
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
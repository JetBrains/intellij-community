// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency.annotations

import org.jetbrains.annotations.ApiStatus

/**
 * Functions annotated with `RequiresBlockingContext` are not designed to be called in suspend context
 * (where [kotlinx.coroutines.currentCoroutineContext] is available).
 *
 * A function should be annotated if there exists an analog of that function which is tailored for the suspending world.
 * For example:
 * ```
 * @RequiresBlockingContext
 * fun writeActionBlocking(action: () -> Unit) {
 *   ...
 * }
 *
 * suspend fun writeAction(action: () -> Unit) {
 *   ...
 * }
 *
 * suspend fun usage() {
 *   writeActionBlocking { // highlighted because the function is annotated
 *     ...
 *   }
 *   writeAction { // a proper function to call in suspend context
 *     ...
 *   }
 * }
 * ```
 *
 * The annotation shall not be propagated to outer functions by default.
 * If there is no analog of the function for the suspending world,
 * then don't annotate it.
 * For example:
 * ```
 * fun addSdk(sdk: Sdk) {
 *   writeActionBlocking { // fine because the context is not suspending
 *     ...
 *   }
 * }
 * ```
 * If `addSdk` is also annotated with `@RequiresBlockingContext`,
 * then a warning would be produced when this function is called from a suspending context,
 * despite there is nothing else to call:
 * ```
 * suspend fun usage() {
 *   addSdk(sdk) // a warning here would be misleading
 * }
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
)
@ApiStatus.Experimental
annotation class RequiresBlockingContext

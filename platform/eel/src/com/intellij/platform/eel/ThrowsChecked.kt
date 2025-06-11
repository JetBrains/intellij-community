// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

/**
 * An annotation for checked exceptions in Kotlin. Works together with `KotlinCheckedExceptionInspection`.
 *
 * Differences from Java's checked exceptions:
 *  * This inspection produces warnings and doesn't break compilation.
 *  * By design, it's fine to suppress the exception.
 *  * The inspection should not try to prove in every case that some exception is not caught.
 *    It's supposed only to remind API users about exceptions.
 *
 * Possible declarations:
 *
 * ```kotlin
 * @ThrowsChecked(MyException::class)
 * fun foobar() {
 *   something()
 * }
 *
 * val handler: @ThrowsChecked(MyException::class) () -> Unit = {
 *   something()
 * }
 *
 * fun usage() {
 *   foobar()  // warning
 *   handler() // warning
 * }
 * ```
 */
@ApiStatus.Internal
@Retention(AnnotationRetention.SOURCE)
@Target(
  AnnotationTarget.CONSTRUCTOR,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.TYPE,
)
annotation class ThrowsChecked(vararg val exceptionClasses: KClass<out Throwable>)
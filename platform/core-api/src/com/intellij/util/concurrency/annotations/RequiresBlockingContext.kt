// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency.annotations

import org.jetbrains.annotations.ApiStatus

/**
 * Methods annotated with `RequiresBlockingContext` are not designed to be called in suspend context
 * (where [kotlinx.coroutines.currentCoroutineContext] is available).
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

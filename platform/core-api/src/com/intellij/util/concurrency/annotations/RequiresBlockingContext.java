// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency.annotations;

import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.*;

/**
 * Methods annotated with {@code RequiresBlockingContext} are not designed to be called in suspend context
 * (where {@code currentCoroutineContext} is available).
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
@ApiStatus.Experimental
public @interface RequiresBlockingContext {
}
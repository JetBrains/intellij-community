/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * An annotation which depicts that method returns an unmodifiable value or a variable
 * contains an unmodifiable value. Unmodifiable value means that calling methods which may
 * mutate this value (alter visible behavior) either don't have any effect or throw
 * an exception. Also unlike unmodifiable view (see {@link UnmodifiableView}) unmodifiable value cannot
 * be modified by any other code as well.
 * <p>
 * This annotation is experimental and may be changed/removed in future
 * without additional notice!
 * </p>
 * @see UnmodifiableView
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE_USE})
@ApiStatus.Experimental
public @interface Unmodifiable {
}
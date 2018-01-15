/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * An annotation which depicts that method returns an unmodifiable value or a variable
 * contains an unmodifiable view. Unmodifiable view means that calling methods which may
 * mutate this value (alter visible behavior) either don't have any effect or throw
 * an exception. However this value could be modified by third-party, thus methods reading
 * the object content might return different result.
 * <p>
 * This annotation is experimental and may be changed/removed in future
 * without additional notice!
 * </p>
 * @see Unmodifiable
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE_USE})
@ApiStatus.Experimental
public @interface UnmodifiableView {
}
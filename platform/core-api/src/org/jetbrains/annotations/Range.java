/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.annotations;

import java.lang.annotation.*;

/**
 * An annotation which allows to specify for integral type (byte, char, short, int, long) an allowed values range.
 * <p>
 * This is an experimental annotation which can be changed or removed without additional notice!
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE_USE})
@ApiStatus.Experimental
public @interface Range {
  /**
   * @return minimal allowed value (inclusive)
   */
  long from();

  /**
   * @return maximal allowed value (inclusive)
   */
  long to();
}
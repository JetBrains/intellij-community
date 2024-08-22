// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an Extension Point bean class whose extensions can safely be Kotlin objects.
 * <p/>
 * The {@link #value} is the attribute name for the instance field name, or empty if extensions do not need to declare one.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface SupportsKotlinObjects {

  /**
   * @return the attribute name for the instance field name, or empty if extensions do not need to declare one.
   */
  String value() default "";
}

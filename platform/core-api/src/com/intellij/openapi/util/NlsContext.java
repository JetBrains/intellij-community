// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.ANNOTATION_TYPE)
public @interface NlsContext {
  /**
   * Provide a neat property key prefix that unambiguously defines literal usage context.
   * E.g. "button", "button.tooltip" for button text and tooltip correspondingly, "action.text" for action text
   */
  @NonNls String prefix() default "";

  /**
   * Provide a neat property key suffix that unambiguously defines literal usage context.
   * E.g. "description" for action/intention description
   */
  @NonNls String suffix() default "";
}
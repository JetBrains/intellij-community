// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

public @interface NlsContext {
  /**
   * Provide a neat property key prefix that unambiguously defines literal usage context.
   * E.g. "button", "button.tooltip" for button text and tooltip correspondingly, "action.text" for action text
   */
  String prefix() default "";

  /**
   * Provide a neat property key suffix that unambiguously defines literal usage context.
   * E.g. "description" for action/intention description
   */
  String suffix() default "";
}
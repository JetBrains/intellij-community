// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks final class as "Light Service".
 * <p>
 * See <a href="https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_services.html#light-services">Light Services</a>.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Service {
  Level[] value() default {};

  enum Level {
    APP, PROJECT
  }
}

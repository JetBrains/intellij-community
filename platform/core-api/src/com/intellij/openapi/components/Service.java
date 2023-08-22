// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.project.Project;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks final class as "Light Service".
 * <p>
 *
 * @see <a href="https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_services.html#light-services">Light Services</a>.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Service {

  /**
   * @return The level(s) on which this service is registered. Default: {@link Level#APP application-level}.
   */
  Level[] value() default Level.APP;

  enum Level {
    /**
     * Application-level instance.
     */
    APP,

    /**
     * Project-level instance, can take {@link Project} as constructor parameter.
     */
    PROJECT
  }
}

/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;

/**
 * Register implementation of this class as {@code projectConfigurable} or {@code applicationConfigurable} extension to provide items for
 * "Project Settings" and "IDE Settings" groups correspondingly in the "Settings" dialog
 *
 * @see Configurable
 * @see Configurable.WithEpDependencies
 */
public abstract class ConfigurableProvider {

  @Nullable
  public abstract Configurable createConfigurable();

  /**
   * Defines whether this provider creates a configurable or not.
   * Note that the {@code createConfigurable} method will be called
   * if this method returns {@code true}.
   *
   * @return {@code true} if this provider creates configurable,
   *         {@code false} otherwise
   */
  public boolean canCreateConfigurable() {
    return true;
  }
}

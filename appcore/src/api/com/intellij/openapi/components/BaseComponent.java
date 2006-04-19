/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.components;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * The base interface class for all components.
 *
 * @see ApplicationComponent
 * @see ProjectComponent
 */
public interface BaseComponent {
  /**
   * Unique name of this component. If there is another component with the same name or
   * name is null internal assertion will occur.
   *
   * @return the name of this component
   */
  @NonNls @NotNull
  String getComponentName();

  /**
   *  Component should do initialization and communication with another components in this method.
   */
  void initComponent();

  /**
   *  Component should dispose system resources or perform another cleanup in this method.
   */
  void disposeComponent();
}

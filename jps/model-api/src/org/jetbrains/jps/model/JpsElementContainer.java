/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public interface JpsElementContainer {
  <T extends JpsElement>
  T getChild(@NotNull JpsElementChildRole<T> role);

  @NotNull
  <T extends JpsElement, K extends JpsElementChildRole<T> &JpsElementCreator<T>>
  T setChild(@NotNull K role);

  @NotNull
  <T extends JpsElement, K extends JpsElementChildRole<T> &JpsElementCreator<T>>
  T getOrSetChild(@NotNull K role);

  @NotNull
  <T extends JpsElement, P, K extends JpsElementChildRole<T> &JpsElementParameterizedCreator<T, P>>
  T setChild(@NotNull K role, @NotNull P param);

  <T extends JpsElement, P, K extends JpsElementChildRole<T> &JpsElementParameterizedCreator<T, P>>
  T getOrSetChild(@NotNull K role, @NotNull Supplier<P> param);

  <T extends JpsElement>
  T setChild(JpsElementChildRole<T> role, T child);

  <T extends JpsElement>
  void removeChild(@NotNull JpsElementChildRole<T> role);
}

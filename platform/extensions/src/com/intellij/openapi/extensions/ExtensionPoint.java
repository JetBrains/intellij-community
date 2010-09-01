/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author AKireyev
 */
public interface ExtensionPoint<T> {
  String getName();
  AreaInstance getArea();

  /**
   * @deprecated use {@link #getClassName()} instead
   */
  String getBeanClassName();

  void registerExtension(@NotNull T extension);
  void registerExtension(@NotNull T extension, @NotNull LoadingOrder order);

  @NotNull
  T[] getExtensions();
  boolean hasAnyExtensions();

  @Nullable
  T getExtension();
  boolean hasExtension(@NotNull T extension);

  void unregisterExtension(@NotNull T extension);

  void addExtensionPointListener(@NotNull ExtensionPointListener<T> listener);
  void removeExtensionPointListener(@NotNull ExtensionPointListener<T> extensionPointListener);

  void reset();

  Class<T> getExtensionClass();

  Kind getKind();

  String getClassName();

  enum Kind {INTERFACE, BEAN_CLASS}
}

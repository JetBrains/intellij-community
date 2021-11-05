/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public interface SmartSelectProvider<T> {
  ExtensionPointName<SmartSelectProvider> EP = ExtensionPointName.create("com.intellij.smartSelectProvider");

  void increaseSelection(T source);

  void decreaseSelection(T source);

  default boolean isEnabled(DataContext context) {
    return getSource(context) != null;
  }

  boolean canIncreaseSelection(T source);

  boolean canDecreaseSelection(T source);

  @Nullable
  T getSource(DataContext context);
}

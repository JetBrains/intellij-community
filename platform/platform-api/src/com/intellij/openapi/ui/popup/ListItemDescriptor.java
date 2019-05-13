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
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author kir
 */
public interface ListItemDescriptor<T> {
  @Nullable
  String getTextFor(T value);

  @Nullable
  String getTooltipFor(T value);

  @Nullable
  Icon getIconFor(T value);

  default Icon getSelectedIconFor(T value) {
    return getIconFor(value);
  }

  boolean hasSeparatorAboveOf(T value);

  @Nullable
  String getCaptionAboveOf(T value);
}

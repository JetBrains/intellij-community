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
package com.intellij.navigation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ItemPresentation {
  /**
   * @return object name to be presented in most renderers across the program.
   */
  String getPresentableText();

  /**
   * @return location info to be used by some renderers to present additional info on item's location. Usually displayed as grayed text next to item name (like class's package)
   */
  String getLocationString();

  /**
   * @param open only meaningful when used in a tree renderers. false is passed when icon for other renderers is required.
   * @return icon
   */
  @Nullable
  Icon getIcon(boolean open);

  /**
   * If return value is null default text attributes will be used
   */
  @Nullable
  TextAttributesKey getTextAttributesKey();
}
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
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface ExpandableItemsHandler<T> {
  /**
   * This key is used to disable showing a popup for expandable item.
   * It can be set to a renderer component to disable a popup for specific item.
   *
   * @see com.intellij.util.ui.UIUtil#putClientProperty
   */
  Key<Boolean> RENDERER_DISABLED = Key.create("EXPANDABLE_ITEM_RENDERER_DISABLED");
  // This is flag is set on list when <b>expanded</b> item is being rendered.
  Key<Boolean> EXPANDED_RENDERER = Key.create("ExpandedRenderer") ;
  // This flag is set on component by CellRenderer which wants to use
  // component's bounds as expanded item's geometry.
  Key<Boolean> USE_RENDERER_BOUNDS = Key.create("UseRendererBounds") ;

  void setEnabled(boolean enabled);
  
  boolean isEnabled();

  @NotNull
  Collection<T> getExpandedItems();
}
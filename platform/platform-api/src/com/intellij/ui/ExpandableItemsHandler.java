// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public interface ExpandableItemsHandler<T> {
  /**
   * This key can be set to a component to ignore processing of mouse events.
   */
  Key<Boolean> IGNORE_MOUSE_HOVER = Key.create("IGNORE_MOUSE_HOVER");
  /**
   * This key can be set to a component to ignore processing of item selection.
   */
  Key<Boolean> IGNORE_ITEM_SELECTION = Key.create("IGNORE_ITEM_SELECTION");
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

  @NotNull @Unmodifiable
  Collection<T> getExpandedItems();
}
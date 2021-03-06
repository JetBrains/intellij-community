// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Policy which defines explicit focus component cycle.
 */
public final class ListFocusTraversalPolicy extends LayoutFocusTraversalPolicy {
  private final Component[] myComponents;
  private final Object2IntMap<Component> myComponentToIndex;

  public ListFocusTraversalPolicy(@NotNull List<? extends Component> components) {
    myComponents = components.toArray(new Component[0]);
    myComponentToIndex = indexMap(myComponents);
  }

  @Override
  protected boolean accept(Component aComponent) {
    return super.accept(aComponent) && aComponent.isShowing();
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    return getNextComponent(0);
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    return getPreviousComponent(myComponents.length - 1);
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    if (!myComponentToIndex.containsKey(aComponent)) {
      return null;
    }
    return getNextComponent(myComponentToIndex.getInt(aComponent) + 1);
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    if (!myComponentToIndex.containsKey(aComponent)) {
      return null;
    }
    return getPreviousComponent(myComponentToIndex.getInt(aComponent) - 1);
  }

  @Nullable
  private Component getNextComponent(int startIndex) {
    for (int index = startIndex; index < myComponents.length; index++) {
      Component result = myComponents[index];
      if (accept(result)) {
        return result;
      }
    }
    for (int index = 0; index < startIndex; index++) {
      Component result = myComponents[index];
      if (accept(result)) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  private Component getPreviousComponent(int startIndex) {
    for (int index = startIndex; index >= 0; index--) {
      Component result = myComponents[index];
      if (accept(result)) {
        return result;
      }
    }
    for (int index = myComponents.length - 1; index > startIndex; index--) {
      Component result = myComponents[index];
      if (accept(result)) {
        return result;
      }
    }
    return null;
  }

  private static @NotNull <X> Object2IntMap<X> indexMap(X @NotNull [] array) {
    Object2IntMap<X> map = new Object2IntOpenHashMap<>(array.length);
    for (X x : array) {
      map.putIfAbsent(x, map.size());
    }
    return map;
  }
}

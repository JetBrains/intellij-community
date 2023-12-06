// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
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
  private final ObjectIntMap<Component> myComponentToIndex;

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
    int i = myComponentToIndex.get(aComponent);
    return getNextComponent((i==-1?0:i) + 1);
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    if (!myComponentToIndex.containsKey(aComponent)) {
      return null;
    }
    int i = myComponentToIndex.get(aComponent);
    return getPreviousComponent((i==-1 ?0:i) - 1);
  }

  private @Nullable Component getNextComponent(int startIndex) {
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

  private @Nullable Component getPreviousComponent(int startIndex) {
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

  private static @NotNull <X> ObjectIntMap<X> indexMap(X @NotNull [] array) {
    ObjectIntMap<X> map = new ObjectIntHashMap<>(array.length);
    for (X x : array) {
      if (!map.containsKey(x)) {
        map.put(x, map.size());
      }
    }
    return map;
  }
}

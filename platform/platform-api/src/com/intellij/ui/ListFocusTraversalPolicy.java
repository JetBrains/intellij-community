// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ArrayUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * Policy which defines explicit focus component cycle.
 */
public class ListFocusTraversalPolicy extends ContainerOrderFocusTraversalPolicy {

  private final Component[] myComponents;
  private final TObjectIntHashMap<Component> myComponentToIndex;

  public ListFocusTraversalPolicy(@NotNull List<? extends Component> components) {
    myComponents = components.toArray(new Component[0]);
    myComponentToIndex = indexMap(myComponents);
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    return ArrayUtil.getFirstElement(myComponents);
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    return ArrayUtil.getLastElement(myComponents);
  }

  @Override
  protected boolean accept(Component aComponent) {
    return super.accept(aComponent) && aComponent.isShowing();
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    if (!myComponentToIndex.containsKey(aComponent)) {
      return null;
    }
    for (int index = myComponentToIndex.get(aComponent) + 1; index < myComponents.length; index++) {
      Component result = myComponents[index];
      if (accept(result)) {
        return result;
      }
    }
    return null;
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    if (!myComponentToIndex.containsKey(aComponent)) {
      return null;
    }
    for (int index = myComponentToIndex.get(aComponent) - 1; index >= 0; index--) {
      Component result = myComponents[index];
      if (accept(result)) {
        return result;
      }
    }
    return null;
  }

  @NotNull
  private static <X> TObjectIntHashMap<X> indexMap(@NotNull X[] array) {
    TObjectIntHashMap<X> map = new TObjectIntHashMap<>(array.length);
    for (X x : array) {
      if (!map.contains(x)) {
        map.put(x, map.size());
      }
    }
    map.compact();
    return map;
  }
}

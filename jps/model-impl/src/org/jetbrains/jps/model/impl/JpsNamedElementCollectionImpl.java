// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementCreator;
import org.jetbrains.jps.model.JpsNamedElement;
import org.jetbrains.jps.model.JpsNamedElementCollection;

import java.util.HashMap;
import java.util.Map;

final class JpsNamedElementCollectionImpl<E extends JpsNamedElement> extends JpsElementCollectionImpl<E> implements
                                                                                                         JpsNamedElementCollection<E> {
  private final Map<String, E> myElementByName = new HashMap<>();
  
  JpsNamedElementCollectionImpl(JpsElementChildRole<E> role) {
    super(role);
  }

  JpsNamedElementCollectionImpl(JpsNamedElementCollectionImpl<E> original) {
    super(original);
    for (E element : getElements()) {
      myElementByName.put(element.getName(), element);
    }
  }

  @Override
  public @Nullable E findChild(@NotNull String name) {
    return myElementByName.get(name);
  }

  @Override
  public @NotNull E addChild(@NotNull JpsElementCreator<E> creator) {
    E child = super.addChild(creator);
    myElementByName.put(child.getName(), child);
    return child;
  }

  @Override
  public <X extends E> X addChild(X element) {
    X child = super.addChild(element);
    myElementByName.put(child.getName(), child);
    return child;
  }

  @Override
  public void removeChild(@NotNull E element) {
    super.removeChild(element);
    myElementByName.remove(element.getName());
  }

  @Override
  public void removeAllChildren() {
    super.removeAllChildren();
    myElementByName.clear();
  }

  @Override
  public @NotNull JpsElementCollectionImpl<E> createCopy() {
    return new JpsNamedElementCollectionImpl<>(this);
  }
}

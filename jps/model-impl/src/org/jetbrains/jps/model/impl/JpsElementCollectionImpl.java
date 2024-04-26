// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.impl;

import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.*;

public final class JpsElementCollectionImpl<E extends JpsElement> extends JpsElementBase<JpsElementCollectionImpl<E>> implements JpsElementCollection<E> {
  private final List<E> myElements;
  private final Map<E, E> myCopyToOriginal;
  private final JpsElementChildRole<E> myChildRole;

  JpsElementCollectionImpl(JpsElementChildRole<E> role) {
    myChildRole = role;
    myElements = new ArrayList<>();
    myCopyToOriginal = null;
  }

  private JpsElementCollectionImpl(JpsElementCollectionImpl<E> original) {
    myChildRole = original.myChildRole;
    myElements = new ArrayList<>(original.myElements.size());
    myCopyToOriginal = new HashMap<>(original.myElements.size());
    for (E e : original.myElements) {
      //noinspection unchecked
      final E copy = (E)e.getBulkModificationSupport().createCopy();
      setParent(copy, this);
      myElements.add(copy);
      myCopyToOriginal.put(copy, e);
    }
  }

  @Override
  public List<E> getElements() {
    return myElements;
  }

  @Override
  public <X extends JpsTypedElement<P>, P extends JpsElement> Iterable<X> getElementsOfType(final @NotNull JpsElementType<P> type) {
    return new JpsElementIterable<>(type);
  }

  @Override
  public @NotNull E addChild(@NotNull JpsElementCreator<E> creator) {
    return addChild(creator.create());
  }

  @Override
  public <X extends E> X addChild(X element) {
    myElements.add(element);
    setParent(element, this);
    return element;
  }

  @Override
  public void removeChild(@NotNull E element) {
    final boolean removed = myElements.remove(element);
    if (removed) {
      setParent(element, null);
    }
  }

  @Override
  public void removeAllChildren() {
    List<E> elements = new ArrayList<>(myElements);
    for (E element : elements) {
      removeChild(element);
    }
  }

  @Override
  public @NotNull JpsElementCollectionImpl<E> createCopy() {
    return new JpsElementCollectionImpl<>(this);
  }

  private final class JpsElementIterable<X extends JpsTypedElement<P>, P extends JpsElement> implements Iterable<X> {
    private final JpsElementType<? extends JpsElement> myType;

    JpsElementIterable(JpsElementType<P> type) {
      myType = type;
    }

    @Override
    public Iterator<X> iterator() {
      //noinspection unchecked
      Iterator<JpsTypedElement<?>> iterator = (Iterator<JpsTypedElement<?>>)myElements.iterator();
      return new FilteringIterator<>(iterator, e -> e.getType().equals(myType));
    }
  }
}

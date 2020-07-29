// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Experimental
public final class IteratorUtils {
  public static <T> boolean match(@NotNull Iterator<T> iterator1,
                                  @NotNull Iterator<T> iterator2,
                                  @NotNull BooleanBiFunction<? super T, ? super T> condition) {
    while (iterator2.hasNext()) {
      if (!iterator1.hasNext() || !condition.fun(iterator1.next(), iterator2.next())) {
        return false;
      }
    }
    return !iterator1.hasNext();
  }

  public static <T> boolean match(@NotNull AbstractObjectGraphIterator<T> iterator1,
                                  @NotNull AbstractObjectGraphIterator<T> iterator2,
                                  @NotNull BooleanBiFunction<? super T, ? super T> condition) {
    while (iterator2.hasNext()) {
      if (!iterator1.hasNext() ||
          !iterator1.myProcessedStructure.equals(iterator2.myProcessedStructure) ||
          !condition.fun(iterator1.next(), iterator2.next())) {
        return false;
      }
    }
    return !iterator1.hasNext();
  }

  @ApiStatus.Experimental
  public abstract static class AbstractObjectGraphIterator<T> implements Iterator<T> {
    private final Set<T> mySeenObjects;
    private final LinkedList<T> myToProcess;
    private final LinkedList<Integer> myProcessedStructure;

    public AbstractObjectGraphIterator(@NotNull Collection<T> dependencies) {
      mySeenObjects = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
      myToProcess = new LinkedList<T>(dependencies);
      myProcessedStructure = new LinkedList<Integer>();
    }

    public abstract Collection<? extends T> getChildren(T t);

    @Override
    public boolean hasNext() {
      T dependency = myToProcess.peekFirst();
      if (dependency == null) return false;
      if (mySeenObjects.contains(dependency)) {
        myToProcess.removeFirst();
        return hasNext();
      }
      return !myToProcess.isEmpty();
    }

    @Override
    public T next() {
      T dependency = myToProcess.removeFirst();
      if (mySeenObjects.add(dependency)) {
        Collection<? extends T> children = getChildren(dependency);
        myToProcess.addAll(children);
        myProcessedStructure.add(children.size());
        return dependency;
      }
      else {
        return next();
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }
  }
}

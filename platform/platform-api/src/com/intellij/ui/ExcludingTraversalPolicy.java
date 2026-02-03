// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ExcludingTraversalPolicy extends FocusTraversalPolicy {
  private final FocusTraversalPolicy myWrappee;
  private final Set<Component> myExcludes = new HashSet<>();
  private final Set<String> myRecursionGuard = new HashSet<>();

  public ExcludingTraversalPolicy(Component... excludes) {
    this(KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalPolicy(), excludes);
  }

  public ExcludingTraversalPolicy(@NotNull FocusTraversalPolicy wrappee, Component... excludes) {
    myWrappee = wrappee;
    Collections.addAll(myExcludes, excludes);
  }

  public void exclude(Component c) {
    myExcludes.add(c);
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    try {
      if (!myRecursionGuard.add("getComponentAfter")) return null;

      return traverse(aContainer, aComponent, param -> myWrappee.getComponentAfter(param.first, param.second));
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    try {
      if (!myRecursionGuard.add("getComponentBefore")) return null;

      return traverse(aContainer, aComponent, param -> myWrappee.getComponentBefore(param.first, param.second));
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  private Component traverse(Container aContainer, Component aComponent, Function<? super Pair<Container, Component>, ? extends Component> func) {
    Set<Component> loopGuard = new HashSet<>();
    do {
      if (!loopGuard.add(aComponent)) return null;
      aComponent = func.fun(Pair.create(aContainer, aComponent));
    }
    while (aComponent != null && myExcludes.contains(aComponent));
    return aComponent;
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    try {
      if (!myRecursionGuard.add("getFirstComponent")) return null;

      Component result = myWrappee.getFirstComponent(aContainer);
      if (result == null) return null;
      return myExcludes.contains(result) ? getComponentAfter(aContainer, result) : result;
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    try {
      if (!myRecursionGuard.add("getLastComponent")) return null;

      Component result = myWrappee.getLastComponent(aContainer);
      if (result == null) return null;
      return myExcludes.contains(result) ? getComponentBefore(aContainer, result) : result;
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    try {
      if (!myRecursionGuard.add("getDefaultComponent")) return null;

      return getFirstComponent(aContainer);
    }
    finally {
      myRecursionGuard.clear();
    }
  }
}

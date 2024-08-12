// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

public final class PathPattern {
  private final List<ElementPattern> myPath = new SmartList<>();

  private PathPattern() {
  }

  public static PathPattern path() {
    return new PathPattern();
  }

  public PathPattern up() {
    myPath.add(null);
    return this;
  }

  public PathPattern left() {
    return left(PlatformPatterns.elementType());
  }

  public PathPattern left(@NotNull IElementType pattern) {
    return left(PlatformPatterns.elementType().equalTo(pattern));
  }

  public PathPattern left(@NotNull ElementPattern pattern) {
    myPath.add(pattern);
    return this;
  }

  public @NonNls String toString() {
    return Arrays.toString(myPath.toArray()).replaceAll("null", "UP");
  }

  public boolean accepts(PrattBuilder builder) {
    ListIterator<IElementType> iterator = null;
    for (final ElementPattern pattern : myPath) {
      if (builder == null) return false;

      if (iterator == null) {
        iterator = builder.getBackResultIterator();
      }

      if (pattern == null) {
        if (iterator.hasPrevious()) return false;
        builder = builder.getParent();
        iterator = null;
      } else {
        if (!iterator.hasPrevious()) return false;
        if (!pattern.accepts(iterator.previous())) return false;
      }
    }

    return true;
  }

}

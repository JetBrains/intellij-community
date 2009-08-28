/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
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

/**
 * @author peter
 */
public class PathPattern {
  private final List<ElementPattern> myPath = new SmartList<ElementPattern>();

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

  @NonNls
  public String toString() {
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

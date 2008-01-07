/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.patterns.impl.Pattern;
import com.intellij.patterns.impl.StandardPatterns;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author peter
 */
public class PathPattern {
  private final List<Pattern<? extends IElementType,?>> myPath = new SmartList<Pattern<? extends IElementType,?>>();

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
    return left(StandardPatterns.elementType());
  }

  public PathPattern left(@NotNull IElementType pattern) {
    return left(StandardPatterns.elementType().equalTo(pattern));
  }

  public PathPattern left(@NotNull Pattern<? extends IElementType,?> pattern) {
    myPath.add(pattern);
    return this;
  }

  @NonNls
  public String toString() {
    return Arrays.toString(myPath.toArray()).replaceAll("null", "UP");
  }

  public boolean accepts(PrattBuilder builder) {
    final Iterator<Object> iterator = builder.getPath().iterator();
    for (final Pattern<? extends IElementType,?> pattern : myPath) {
      if (!iterator.hasNext()) return false;

      final Object pathElement = iterator.next();
      if (pattern == null) {
        if (pathElement != Boolean.TRUE) {
          return false;
        }
      }
      else if (!pattern.accepts(pathElement)) {
        return false;
      }
    }

    return true;
  }
}

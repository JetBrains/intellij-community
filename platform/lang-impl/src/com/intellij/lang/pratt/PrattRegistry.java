// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class PrattRegistry {
  private final MultiMap<IElementType, ParserData> myMap = new MultiMap<>();

  public void registerParser(@NotNull final IElementType type, final int priority, final TokenParser parser) {
    registerParser(type, priority, PathPattern.path(), parser);
  }

  public void registerParser(@NotNull final IElementType type, final int priority, final PathPattern pattern, final TokenParser parser) {
    myMap.putValue(type, new ParserData(priority, pattern, parser));
  }

  public record ParserData(int priority, PathPattern pattern, TokenParser parser) {}

  @NotNull
  public Collection<ParserData> getParsers(@Nullable final IElementType type) {
    return myMap.get(type);
  }

  public void unregisterParser(@NotNull final IElementType type) {
    myMap.remove(type);
  }
}

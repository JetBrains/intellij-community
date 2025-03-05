// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.tree;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * Analogue {@link TokenSet}.
 * The main purpose is to check if a given token type is present in the token set.
 * The main difference is that this class supports hierarchy with {@link ParentProviderElementType}
 */
public final class ParentAwareTokenSet {

  private final @NotNull TokenSet myTokenSet;

  private ParentAwareTokenSet(@NotNull TokenSet set) { myTokenSet = set; }

  /**
   * @return true if the provided token set contains given source element type or any of its parents, otherwise false
   * @see ParentProviderElementType#containsWithSourceParent(IElementType, TokenSet)
   */
  @Contract("null -> false")
  public boolean contains(@Nullable IElementType iElementType) {
    if (iElementType == null) {
      return false;
    }
    return ParentProviderElementType.containsWithSourceParent(iElementType, myTokenSet);
  }

  public static @NotNull ParentAwareTokenSet create(@NotNull TokenSet set) {
    return new ParentAwareTokenSet(set);
  }

  public static @NotNull ParentAwareTokenSet create(@NotNull Set<IElementType> set) {
    return new ParentAwareTokenSet(TokenSet.create(set.toArray(IElementType.EMPTY_ARRAY)));
  }

  public static @NotNull ParentAwareTokenSet orSet(ParentAwareTokenSet... sets) {
    TokenSet tokenSet = TokenSet.orSet(Arrays.stream(sets).map(t -> t.myTokenSet).toArray(TokenSet[]::new));
    return new ParentAwareTokenSet(tokenSet);
  }

  public static @NotNull ParentAwareTokenSet create(IElementType... set) {
    TokenSet tokenSet = TokenSet.create(set);
    return new ParentAwareTokenSet(tokenSet);
  }
}

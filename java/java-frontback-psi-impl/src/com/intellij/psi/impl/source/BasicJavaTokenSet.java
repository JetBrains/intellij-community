// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

public final class BasicJavaTokenSet {
  private final TokenSet myTokenSet;

  private BasicJavaTokenSet(@NotNull TokenSet set) { myTokenSet = set; }

  public boolean contains(@Nullable IElementType iElementType) {
    if (iElementType == null) {
      return false;
    }
    return BasicJavaAstTreeUtil.is(iElementType, myTokenSet);
  }

  public TokenSet toTokenSet() {
    return TokenSet.forAllMatching(t -> this.contains(t));
  }

  public static BasicJavaTokenSet create(@NotNull TokenSet set) {
    return new BasicJavaTokenSet(set);
  }

  static BasicJavaTokenSet create(@NotNull Set<IElementType> set) {
    return new BasicJavaTokenSet(TokenSet.create(set.toArray(IElementType.EMPTY_ARRAY)));
  }

  public static BasicJavaTokenSet orSet(BasicJavaTokenSet... sets) {
    TokenSet tokenSet = TokenSet.orSet(Arrays.stream(sets).map(t -> t.myTokenSet).toArray(TokenSet[]::new));
    return new BasicJavaTokenSet(tokenSet);
  }

  public static BasicJavaTokenSet create(IElementType... set) {
    TokenSet tokenSet = TokenSet.create(set);
    return new BasicJavaTokenSet(tokenSet);
  }
}

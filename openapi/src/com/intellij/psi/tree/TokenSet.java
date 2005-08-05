/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.tree;

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.Arrays;

public class TokenSet {
  public static final TokenSet EMPTY = new TokenSet();
  private final THashSet<IElementType> mySet = new THashSet<IElementType>(100, (float)0.1, TokenHashingStrategy.INSTANCE) ;

  public IElementType[] getTypes() {
    return mySet.toArray(new IElementType[mySet.size()]);
  }

  public static TokenSet create(IElementType... types) {
    TokenSet set = new TokenSet();
    set.mySet.addAll(Arrays.asList(types));
    return set;
  }

  public static TokenSet orSet(TokenSet... sets) {
    TokenSet newSet = new TokenSet();
    for (TokenSet set : sets) {
      newSet.mySet.addAll(set.mySet);
    }
    return newSet;
  }

  public static TokenSet andSet(TokenSet a, TokenSet b) {
    TokenSet set = new TokenSet();
    set.mySet.addAll(a.mySet);
    set.mySet.retainAll(b.mySet);
    return set;
  }

  /**
   * @deprecated use {@link #contains(IElementType)} instead. This appears to be a better naming.
   */
  public boolean isInSet(IElementType t) {
    return mySet.contains(t);
  }

  public boolean contains(IElementType t) {
    return mySet.contains(t);
  }

  private static class TokenHashingStrategy implements TObjectHashingStrategy<IElementType> {
    public static TokenHashingStrategy INSTANCE = new TokenHashingStrategy();
    public int computeHashCode(final IElementType token) {
      return token != null ? token.getIndex() : 0;
    }

    public boolean equals(final IElementType t1, final IElementType t2) {
      return t1 == t2;
    }
  }
}
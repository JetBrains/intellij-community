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

/**
 * A set of element types.
 */

public class TokenSet {
  public static final TokenSet EMPTY = new TokenSet();
  private final boolean[] mySet = new boolean[IElementType.getAllocatedTypesCount()] ;

  /**
   * Returns the array of element types contained in the set.
   *
   * @return the contents of the set.
   */

  public IElementType[] getTypes() {
    int elementCount = 0;
    for (boolean bit : mySet) {
      if (bit) elementCount++;
    }

    IElementType[] types = new IElementType[elementCount];
    int count = 0;
    for (short i = 0; i < mySet.length; i++) {
      if (mySet[i]) {
        types[count++] = IElementType.find(i);
      }
    }

    return types;
  }

  /**
   * Returns a new token set containing the specified element types.
   *
   * @param types the element types contained in the set.
   * @return the new token set.
   */

  public static TokenSet create(IElementType... types) {
    TokenSet set = new TokenSet();
    for (IElementType type : types) {
      set.mySet[type.getIndex()] = true;
    }
    return set;
  }

  /**
   * Returns a token set containing the union of the specified token sets.
   *
   * @param sets the token sets to unio.
   * @return the new token set.
   */

  public static TokenSet orSet(TokenSet... sets) {
    TokenSet newSet = new TokenSet();
    for (TokenSet set : sets) {
      for (int i = 0; i < newSet.mySet.length; i++) {
        if (i >= set.mySet.length) break;
        newSet.mySet[i] |= set.mySet[i];
      }
    }
    return newSet;
  }

  /**
   * Returns a token set containing the intersection of the specified token sets.
   *
   * @param a the first token set to intersect.
   * @param b the second token set to intersect.
   * @return the new token set.
   */

  public static TokenSet andSet(TokenSet a, TokenSet b) {
    TokenSet set = new TokenSet();
    final int andSize = Math.max(set.mySet.length, Math.max(a.mySet.length, b.mySet.length));

    for (int i = 0; i < andSize; i++) {
      set.mySet[i] = a.mySet[i] && b.mySet[i];
    }
    return set;
  }

  /**
   * @deprecated use {@link #contains(IElementType)} instead. This appears to be a better naming.
   */
  public boolean isInSet(IElementType t) {
    final short i = t.getIndex();
    return i < mySet.length && mySet[i];
  }

  /**
   * Checks if the specified element type is contained in the set.
   *
   * @param t the element type to search for.
   * @return true if the element type is found in the set, false otherwise.
   */
  public boolean contains(IElementType t) {
    if (t == null) return false;
    final short i = t.getIndex();
    return 0 <= i && i < mySet.length && mySet[i];
  }
}
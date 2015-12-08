/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A set of element types.
 */
public class TokenSet {
  public static final TokenSet EMPTY = new TokenSet(Short.MAX_VALUE, (short)0) {
    @Override public boolean contains(IElementType t) { return false; }
  };

  private final short myShift;
  private final short myMax;
  private final long[] myWords;
  private volatile IElementType[] myTypes;

  private TokenSet(short shift, short max) {
    myShift = shift;
    myMax = max;
    final int size = (max >> 6) + 1 - shift;
    myWords = size > 0 ? new long[size] : ArrayUtil.EMPTY_LONG_ARRAY;
  }

  private boolean get(int index) {
    final int wordIndex = (index >> 6) - myShift;
    return wordIndex >= 0 && wordIndex < myWords.length && (myWords[wordIndex] & (1L << index)) != 0;
  }

  /**
   * Checks if the specified element type is contained in the set.
   *
   * @param t the element type to search for.
   * @return true if the element type is found in the set, false otherwise.
   */
  public boolean contains(@Nullable IElementType t) {
    if (t == null) return false;
    final short i = t.getIndex();
    return 0 <= i && i <= myMax && get(i);
  }

  /**
   * Returns the array of element types contained in the set.
   *
   * @return the contents of the set.
   */
  @NotNull
  public IElementType[] getTypes() {
    IElementType[] types = myTypes;

    if (types == null) {
      if (myWords.length == 0) {
        types = IElementType.EMPTY_ARRAY;
      }
      else {
        List<IElementType> list = new ArrayList<IElementType>();
        for (short i = (short)Math.max(1, myShift << 6); i <= myMax; i++) {
          if (!get(i)) continue;
          IElementType type = IElementType.find(i);
          if (type != null) {
            list.add(type);
          }
        }
        types = list.toArray(new IElementType[list.size()]);
      }
      myTypes = types;
    }

    return types;
  }

  @Override
  public String toString() {
    return Arrays.toString(getTypes());
  }

  /**
   * Returns a new token set containing the specified element types.
   *
   * @param types the element types contained in the set.
   * @return the new token set.
   */
  @NotNull
  public static TokenSet create(@NotNull IElementType... types) {
    if (types.length == 0) return EMPTY;

    short min = Short.MAX_VALUE;
    short max = 0;
    for (IElementType type : types) {
      if (type != null) {
        final short index = type.getIndex();
        assert index >= 0 : "Unregistered elements are not allowed here: " + LogUtil.objectAndClass(type);
        if (min > index) min = index;
        if (max < index) max = index;
      }
    }

    final short shift = (short)(min >> 6);
    final TokenSet set = new TokenSet(shift, max);
    for (IElementType type : types) {
      if (type != null) {
        final short index = type.getIndex();
        final int wordIndex = (index >> 6) - shift;
        set.myWords[wordIndex] |= 1L << index;
      }
    }
    return set;
  }

  /**
   * Returns a token set containing the union of the specified token sets.
   *
   * @param sets the token sets to unite.
   * @return the new token set.
   */
  @NotNull
  public static TokenSet orSet(@NotNull TokenSet... sets) {
    if (sets.length == 0) return EMPTY;

    short shift = sets[0].myShift;
    short max = sets[0].myMax;
    for (int i = 1; i < sets.length; i++) {
      if (shift > sets[i].myShift) shift = sets[i].myShift;
      if (max < sets[i].myMax) max = sets[i].myMax;
    }

    final TokenSet newSet = new TokenSet(shift, max);
    for (TokenSet set : sets) {
      final int shiftDiff = set.myShift - newSet.myShift;
      for (int i = 0; i < set.myWords.length; i++) {
        newSet.myWords[i + shiftDiff] |= set.myWords[i];
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
  @NotNull
  public static TokenSet andSet(@NotNull TokenSet a, @NotNull TokenSet b) {
    final TokenSet newSet = new TokenSet((short)Math.min(a.myShift, b.myShift), (short)Math.max(a.myMax, b.myMax));
    for (int i = 0; i < newSet.myWords.length; i++) {
      final int ai = newSet.myShift - a.myShift + i;
      final int bi = newSet.myShift - b.myShift + i;
      newSet.myWords[i] = (0 <= ai && ai < a.myWords.length ? a.myWords[ai] : 0L) & (0 <= bi && bi < b.myWords.length ? b.myWords[bi] : 0L);
    }
    return newSet;
  }

  /**
   * Returns a token set containing a result of "set subtraction" of set B from set A.
   *
   * @param a the basic token set.
   * @param b the token set to subtract.
   * @return the new token set.
   */
  @NotNull
  public static TokenSet andNot(@NotNull TokenSet a, @NotNull TokenSet b) {
    final TokenSet newSet = new TokenSet((short)Math.min(a.myShift, b.myShift), (short)Math.max(a.myMax, b.myMax));
    for (int i = 0; i < newSet.myWords.length; i++) {
      final int ai = newSet.myShift - a.myShift + i;
      final int bi = newSet.myShift - b.myShift + i;
      newSet.myWords[i] = (0 <= ai && ai < a.myWords.length ? a.myWords[ai] : 0L) & ~(0 <= bi && bi < b.myWords.length ? b.myWords[bi] : 0L);
    }
    return newSet;
  }
}

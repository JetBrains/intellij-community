// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.tree;

import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.psi.TokenType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A set of element types.
 */
public class TokenSet {
  public static final TokenSet EMPTY = new TokenSet(Short.MAX_VALUE, (short)0, null);
  public static final TokenSet ANY = forAllMatching(IElementType.TRUE);
  public static final TokenSet WHITE_SPACE = doCreate(TokenType.WHITE_SPACE);

  private final short myShift;
  private final short myMax;
  private final long[] myWords;
  private final @Nullable IElementType.Predicate myOrCondition;
  private volatile IElementType[] myTypes;

  private TokenSet(short shift, short max, @Nullable IElementType.Predicate orCondition) {
    myShift = shift;
    myMax = max;
    final int size = (max >> 6) + 1 - shift;
    myWords = size > 0 ? new long[size] : ArrayUtil.EMPTY_LONG_ARRAY;
    myOrCondition = orCondition;
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
  @Contract("null -> false")
  public boolean contains(@Nullable IElementType t) {
    if (t == null) return false;
    final short i = t.getIndex();
    return 0 <= i && i <= myMax && get(i) || myOrCondition != null && myOrCondition.matches(t);
  }

  /**
   * Returns the array of element types contained in the set.
   *
   * @return the contents of the set.
   */
  @NotNull
  public IElementType[] getTypes() {
    if (myOrCondition != null) {
      // don't cache, since new element types matching the given condition can be registered at any moment
      return IElementType.enumerate(this::contains);
    }

    IElementType[] types = myTypes;

    if (types == null) {
      if (myWords.length == 0) {
        types = IElementType.EMPTY_ARRAY;
      }
      else {
        List<IElementType> list = new ArrayList<>();
        for (short i = (short)Math.max(1, myShift << 6); i <= myMax; i++) {
          if (!get(i)) continue;
          IElementType type = IElementType.find(i);
          if (type != null) {
            list.add(type);
          }
        }
        types = list.toArray(IElementType.EMPTY_ARRAY);
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
    if (types.length == 1 && types[0] == TokenType.WHITE_SPACE) {
      return WHITE_SPACE;
    }
    return doCreate(types);
  }

  @NotNull
  private static TokenSet doCreate(@NotNull IElementType... types) {
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

    short shift = (short)(min >> 6);
    TokenSet set = new TokenSet(shift, max, null);
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
   * @return a token set containing all element types satisfying the given condition, including ones registered after this set creation
   */
  public static TokenSet forAllMatching(@NotNull IElementType.Predicate condition) {
    return new TokenSet(Short.MAX_VALUE, (short)0, condition);
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

    List<IElementType.Predicate> orConditions = new ArrayList<>();
    ContainerUtil.addIfNotNull(orConditions, sets[0].myOrCondition);

    short shift = sets[0].myShift;
    short max = sets[0].myMax;
    for (int i = 1; i < sets.length; i++) {
      if (shift > sets[i].myShift) shift = sets[i].myShift;
      if (max < sets[i].myMax) max = sets[i].myMax;
      ContainerUtil.addIfNotNull(orConditions, sets[i].myOrCondition);
    }

    IElementType.Predicate disjunction =
      orConditions.isEmpty() ? null :
      orConditions.size() == 1 ? orConditions.get(0) :
      new OrPredicate(orConditions);
    TokenSet newSet = new TokenSet(shift, max, disjunction);
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
    List<IElementType.Predicate> orConditions = new ArrayList<>();
    ContainerUtil.addIfNotNull(orConditions, a.myOrCondition);
    ContainerUtil.addIfNotNull(orConditions, b.myOrCondition);

    IElementType.Predicate conjunction =
      orConditions.isEmpty() ? null :
      orConditions.size() == 1 ? orConditions.get(0) :
      t -> Objects.requireNonNull(a.myOrCondition).matches(t) && Objects.requireNonNull(b.myOrCondition).matches(t);
    TokenSet newSet = new TokenSet((short)Math.min(a.myShift, b.myShift), (short)Math.max(a.myMax, b.myMax), conjunction);
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
    IElementType.Predicate difference = a.myOrCondition == null ? null : e -> !b.contains(e) && a.myOrCondition.matches(e);
    TokenSet newSet = new TokenSet((short)Math.min(a.myShift, b.myShift), (short)Math.max(a.myMax, b.myMax), difference);
    for (int i = 0; i < newSet.myWords.length; i++) {
      final int ai = newSet.myShift - a.myShift + i;
      final int bi = newSet.myShift - b.myShift + i;
      newSet.myWords[i] = (0 <= ai && ai < a.myWords.length ? a.myWords[ai] : 0L) & ~(0 <= bi && bi < b.myWords.length ? b.myWords[bi] : 0L);
    }
    return newSet;
  }

  private static class OrPredicate implements IElementType.Predicate {
    private final IElementType.Predicate[] myComponents;

    OrPredicate(List<IElementType.Predicate> components) {
      myComponents = components.stream()
        .flatMap(p -> p instanceof OrPredicate ? Arrays.stream(((OrPredicate)p).myComponents) : Stream.of(p))
        .distinct()
        .toArray(IElementType.Predicate[]::new);
    }

    @Override
    public boolean matches(@NotNull IElementType t) {
      return Arrays.stream(myComponents).anyMatch(component -> component.matches(t));
    }
  }
}
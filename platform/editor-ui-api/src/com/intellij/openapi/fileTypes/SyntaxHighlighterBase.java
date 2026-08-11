// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class SyntaxHighlighterBase implements SyntaxHighlighter {
  private static final Logger LOG = Logger.getInstance(SyntaxHighlighterBase.class);

  /**
   * @deprecated Use {@link TextAttributesKey#EMPTY_ARRAY} instead
   */
  @Deprecated
  protected static final TextAttributesKey[] EMPTY = TextAttributesKey.EMPTY_ARRAY;

  public static @NotNull TextAttributesKey @NotNull [] pack(@Nullable TextAttributesKey key) {
    return key == null ? TextAttributesKey.EMPTY_ARRAY : new TextAttributesKey[]{key};
  }

  public static @NotNull TextAttributesKey @NotNull [] pack(@Nullable TextAttributesKey key1, @Nullable TextAttributesKey key2) {
    if (key1 == null || key1.equals(key2)) return pack(key2);
    if (key2 == null) return pack(key1);
    return new TextAttributesKey[]{key1, key2};
  }

  public static @NotNull TextAttributesKey @NotNull [] pack(@NotNull TextAttributesKey @NotNull [] base, @Nullable TextAttributesKey key) {
    assertNoNulls(base);
    return key == null || ArrayUtil.contains(key,base) ? base : ArrayUtil.append(base, key, TextAttributesKey.ARRAY_FACTORY);
  }

  public static @NotNull TextAttributesKey @NotNull [] pack(@Nullable TextAttributesKey key, @NotNull TextAttributesKey @NotNull [] base) {
    assertNoNulls(base);
    return key == null || ArrayUtil.contains(key,base) ? base : ArrayUtil.prepend(key, base, TextAttributesKey.ARRAY_FACTORY);
  }

  public static @NotNull TextAttributesKey @NotNull [] pack(@NotNull TextAttributesKey @NotNull [] base, @Nullable TextAttributesKey t1, @Nullable TextAttributesKey t2) {
    assertNoNulls(base);
    int newSize = base.length + (t1 == null ? 0 : 1) + (t2 == null ? 0 : 1);
    if (newSize == base.length) {
      return base;
    }
    TextAttributesKey[] result = new TextAttributesKey[newSize];
    System.arraycopy(base, 0, result, 0, base.length);
    if (t1 != null) {
      result[base.length] = t1;
    }
    if (t2 != null) {
      result[newSize - 1] = t2;
    }
    return result;
  }

  private static void assertNoNulls(TextAttributesKey @NotNull [] base) {
    if (ArrayUtil.contains(null, base)) {
      throw new IllegalArgumentException("Must not pass nulls but got: " + Arrays.toString(base));
    }
  }

  public static void fillMap(@NotNull Map<? super @NotNull IElementType, ? super @NotNull TextAttributesKey> map, @NotNull TokenSet keys, @NotNull TextAttributesKey value) {
    fillMap(map, value, keys.getTypes());
  }

  protected static void fillMap(@NotNull Map<? super @NotNull IElementType, ? super @NotNull TextAttributesKey> map, @NotNull TextAttributesKey value, @NotNull IElementType @NotNull ... types) {
    for (IElementType type : types) {
      map.put(type, value);
    }
  }

  /**
   * Tries to update the map by associating given keys with a given value.
   * Throws error if the map already contains different mapping for one of given keys.
   */
  protected static void safeMap(@NotNull Map<? super IElementType, TextAttributesKey> map,
                                @NotNull TokenSet keys,
                                @NotNull TextAttributesKey value) {
    for (IElementType type : keys.getTypes()) {
      safeMap(map, type, value);
    }
  }

  /**
   * Tries to update the map by associating given key with a given value.
   * Throws error if the map already contains different mapping for given key.
   */
  protected static void safeMap(@NotNull Map<? super IElementType, TextAttributesKey> map,
                                @NotNull IElementType type,
                                @NotNull TextAttributesKey value) {
    TextAttributesKey oldVal = map.put(type, value);
    if (oldVal != null && !oldVal.equals(value)) {
      LOG.error("Remapping highlighting for \"" + type + "\" val: old=" + oldVal + " new=" + value);
    }
  }

  /// From two `Map<IElementType, TextAttributesKey>` maps, create `Map<IElementType, TextAttributesKey[]>` map containing keys and values from both sources
  protected static @NotNull @Unmodifiable Map<@NotNull IElementType, @NotNull TextAttributesKey @NotNull []> merge(@NotNull Map<? extends @NotNull IElementType, @NotNull TextAttributesKey> map1,
                                                                                                                   @NotNull Map<? extends @NotNull IElementType, @NotNull TextAttributesKey> map2) {
    Set<IElementType> keys = ContainerUtil.union(map1.keySet(), map2.keySet());
    List<Map.Entry<@NotNull IElementType, @NotNull TextAttributesKey @NotNull []>> entries = new ArrayList<>(keys.size());
    for (IElementType key : keys) {
      TextAttributesKey[] packed = pack(map1.get(key), map2.get(key));
      if (packed != TextAttributesKey.EMPTY_ARRAY) { // optimization: reduce memory
        entries.add(Map.entry(key, packed));
      }
    }
    //noinspection unchecked
    return Map.ofEntries(entries.toArray(new Map.Entry[0]));
  }
}

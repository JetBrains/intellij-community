// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

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
    if (key1 == null) return pack(key2);
    if (key2 == null) return pack(key1);
    return new TextAttributesKey[]{key1, key2};
  }

  public static @NotNull TextAttributesKey @NotNull [] pack(@NotNull TextAttributesKey @NotNull [] base, @Nullable TextAttributesKey key) {
    assertNoNulls(base);
    if (key == null) {
      return base;
    }
    TextAttributesKey[] result = Arrays.copyOf(base, base.length + 1);
    result[base.length] = key;
    return result;
  }

  public static @NotNull TextAttributesKey @NotNull [] pack(@Nullable TextAttributesKey key, @NotNull TextAttributesKey @NotNull [] base) {
    assertNoNulls(base);
    if (key == null) {
      return base;
    }
    TextAttributesKey[] result = new TextAttributesKey[base.length + 1];
    System.arraycopy(base, 0, result, 1, base.length);
    result[0] = key;
    return result;
  }

  public static @NotNull TextAttributesKey @NotNull [] pack(@NotNull TextAttributesKey @NotNull [] base, @Nullable TextAttributesKey t1, @Nullable TextAttributesKey t2) {
    assertNoNulls(base);
    int add = 0;
    if (t1 != null) add++;
    if (t2 != null) add++;
    if (add == 0) return base;
    TextAttributesKey[] result = Arrays.copyOf(base, base.length + add);
    add = base.length;
    if (t1 != null) result[add++] = t1;
    if (t2 != null) result[add] = t2;
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
}

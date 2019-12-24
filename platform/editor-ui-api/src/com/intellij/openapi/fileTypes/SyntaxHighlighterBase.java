// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class SyntaxHighlighterBase implements SyntaxHighlighter {
  private static final Logger LOG = Logger.getInstance(SyntaxHighlighterBase.class);

  /**
   * @deprecated Use {@link TextAttributesKey#EMPTY_ARRAY} instead
   */
  @Deprecated
  protected static final TextAttributesKey[] EMPTY = TextAttributesKey.EMPTY_ARRAY;

  @NotNull
  public static TextAttributesKey[] pack(@Nullable TextAttributesKey key) {
    return key == null ? TextAttributesKey.EMPTY_ARRAY : new TextAttributesKey[]{key};
  }

  @NotNull
  public static TextAttributesKey[] pack(@Nullable TextAttributesKey key1, @Nullable TextAttributesKey key2) {
    if (key1 == null) return pack(key2);
    if (key2 == null) return pack(key1);
    return new TextAttributesKey[]{key1, key2};
  }

  @NotNull
  public static TextAttributesKey[] pack(@NotNull TextAttributesKey[] base, @Nullable TextAttributesKey key) {
    if (key == null) return base;
    TextAttributesKey[] result = new TextAttributesKey[base.length + 1];
    System.arraycopy(base, 0, result, 0, base.length);
    result[base.length] = key;
    return result;
  }

  @NotNull
  public static TextAttributesKey[] pack(@Nullable TextAttributesKey key, @NotNull TextAttributesKey[] base) {
    if (key == null) return base;
    TextAttributesKey[] result = new TextAttributesKey[base.length + 1];
    System.arraycopy(base, 0, result, 1, base.length);
    result[0] = key;
    return result;
  }

  @NotNull
  public static TextAttributesKey[] pack(@NotNull TextAttributesKey[] base, @Nullable TextAttributesKey t1, @Nullable TextAttributesKey t2) {
    int add = 0;
    if (t1 != null) add++;
    if (t2 != null) add++;
    if (add == 0) return base;
    TextAttributesKey[] result = new TextAttributesKey[base.length + add];
    add = base.length;
    System.arraycopy(base, 0, result, 0, base.length);
    if (t1 != null) result[add++] = t1;
    if (t2 != null) result[add] = t2;
    return result;
  }

  public static void fillMap(@NotNull Map<IElementType, TextAttributesKey> map, @NotNull TokenSet keys, TextAttributesKey value) {
    fillMap(map, value, keys.getTypes());
  }

  protected static void fillMap(@NotNull Map<IElementType, TextAttributesKey> map, TextAttributesKey value, @NotNull IElementType... types) {
    for (IElementType type : types) {
      map.put(type, value);
    }
  }

  /**
   * Tries to update the map by associating given keys with a given value.
   * Throws error if the map already contains different mapping for one of given keys.
   */
  protected static void safeMap(@NotNull final Map<IElementType, TextAttributesKey> map,
                                @NotNull final TokenSet keys,
                                @NotNull final TextAttributesKey value) {
    for (final IElementType type : keys.getTypes()) {
      safeMap(map, type, value);
    }
  }

  /**
   * Tries to update the map by associating given key with a given value.
   * Throws error if the map already contains different mapping for given key.
   */
  protected static void safeMap(@NotNull final Map<IElementType, TextAttributesKey> map,
                                @NotNull final IElementType type,
                                @NotNull final TextAttributesKey value) {
    final TextAttributesKey oldVal = map.put(type, value);
    if (oldVal != null && !oldVal.equals(value)) {
      LOG.error("Remapping highlighting for \"" + type + "\" val: old=" + oldVal + " new=" + value);
    }
  }
}

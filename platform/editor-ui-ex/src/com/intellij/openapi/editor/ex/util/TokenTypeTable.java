// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A table of one value for each token type. Any thread can call its methods without a lock.
 * A table keeps the values of the token types with an index that it had at its creation.
 * The tables that come from one {@link #empty()} table share the values of the token types without an index.
 */
final class TokenTypeTable<T> {
  private static final int PAGE_SHIFT = 7;
  private static final int PAGE_SIZE = 1 << PAGE_SHIFT;
  private static final int PAGE_COUNT = (Short.MAX_VALUE + 1) >> PAGE_SHIFT;
  private static final Object[][] NO_PAGES = new Object[PAGE_COUNT][];

  private final Object[][] pages;
  private final ConcurrentHashMap<IElementType, Object> valuesOfTypesWithoutIndex;

  private TokenTypeTable(Object[][] pages, ConcurrentHashMap<IElementType, Object> valuesOfTypesWithoutIndex) {
    this.pages = pages;
    this.valuesOfTypesWithoutIndex = valuesOfTypesWithoutIndex;
  }

  /**
   * Returns a new table with no values.
   */
  static <T> TokenTypeTable<T> empty() {
    return new TokenTypeTable<>(NO_PAGES, new ConcurrentHashMap<>());
  }

  /**
   * Returns the value of {@code tokenType}, or {@code null} when the table has no value for it.
   */
  @SuppressWarnings("unchecked")
  @Nullable T get(IElementType tokenType) {
    int index = tokenType.getIndex();
    if (index < 0) {
      return (T)valuesOfTypesWithoutIndex.get(tokenType);
    }
    return getByIndex(index);
  }

  /**
   * Returns the value of the token type with {@code index}, or {@code null} when the table has no value for it.
   * The {@code index} is not negative, see {@link IElementType#getIndex()}.
   */
  @SuppressWarnings("unchecked")
  @Nullable T getByIndex(int index) {
    Object[] page = pages[index >> PAGE_SHIFT];
    if (page == null) {
      return null;
    }
    return (T)page[index & (PAGE_SIZE - 1)];
  }

  /**
   * Returns a table with the values of this table and {@code value} for {@code tokenType}.
   */
  TokenTypeTable<T> with(IElementType tokenType, T value) {
    int index = tokenType.getIndex();
    if (index < 0) {
      valuesOfTypesWithoutIndex.put(tokenType, value);
      return this;
    }
    Object[][] newPages = pages.clone();
    Object[] page = newPages[index >> PAGE_SHIFT];
    Object[] newPage = page == null ? new Object[PAGE_SIZE] : page.clone();
    newPage[index & (PAGE_SIZE - 1)] = value;
    newPages[index >> PAGE_SHIFT] = newPage;
    return new TokenTypeTable<>(newPages, valuesOfTypesWithoutIndex);
  }
}

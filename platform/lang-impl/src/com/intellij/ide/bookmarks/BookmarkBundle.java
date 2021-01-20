// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class BookmarkBundle extends DynamicBundle {
  private static final @NonNls String BUNDLE = "messages.BookmarkBundle";
  private static final BookmarkBundle INSTANCE = new BookmarkBundle();

  private BookmarkBundle() {
    super(BUNDLE);
  }

  public static @Nls @NotNull String message(
    @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
    @NotNull Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(
    @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
    @NotNull Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

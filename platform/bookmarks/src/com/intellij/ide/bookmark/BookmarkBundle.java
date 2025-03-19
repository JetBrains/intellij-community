// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class BookmarkBundle {
  private static final @NonNls String BUNDLE = "messages.BookmarkBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(BookmarkBundle.class, BUNDLE);

  private BookmarkBundle() {
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

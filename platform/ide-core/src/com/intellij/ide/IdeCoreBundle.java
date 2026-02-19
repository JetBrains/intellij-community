// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class IdeCoreBundle {
  public static final String BUNDLE = "messages.IdeCoreBundle";
  private static final DynamicBundle bundle = new DynamicBundle(IdeCoreBundle.class, BUNDLE);

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return bundle.getMessage(key, params);
  }

  public static @Nullable @Nls String messageOrNull(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return bundle.messageOrNull(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return bundle.getLazyMessage(key, params);
  }
}

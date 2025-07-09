// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class InspectionsDeprecatedMessagesBundle {
  private static final @NonNls String BUNDLE = "messages.InspectionsDeprecatedMessagesBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(InspectionsDeprecatedMessagesBundle.class, BUNDLE);

  private InspectionsDeprecatedMessagesBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

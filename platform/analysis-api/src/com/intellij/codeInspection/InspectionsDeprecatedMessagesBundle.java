// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class InspectionsDeprecatedMessagesBundle extends AbstractBundle {
  private static final @NonNls String BUNDLE = "messages.InspectionsDeprecatedMessagesBundle";
  private static final InspectionsDeprecatedMessagesBundle INSTANCE = new InspectionsDeprecatedMessagesBundle();

  private InspectionsDeprecatedMessagesBundle() {
    super(BUNDLE);
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

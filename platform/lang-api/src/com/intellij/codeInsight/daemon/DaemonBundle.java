// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.DynamicBundle;
import com.intellij.ide.IdeDeprecatedMessagesBundle;
import com.intellij.platform.ide.productMode.IdeProductMode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class DaemonBundle {
  private static final @NonNls String BUNDLE = "messages.DaemonBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(DaemonBundle.class, BUNDLE);

  private DaemonBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.message(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    if (INSTANCE.containsKey(key)) {
      return INSTANCE.getLazyMessage(key, params);
    }
    return IdeDeprecatedMessagesBundle.messagePointer(key, params);
  }

  /**
   * Returns the dumb mode message for the current product mode.
   * Uses {@code lightModeKey} in Light mode and {@code key} in all other modes.
   * Both messages must accept the same {@code params}.
   *
   * @param key          the message key for dumb mode
   * @param lightModeKey the message key for Light mode
   * @param params       the parameters for the selected message
   */
  @ApiStatus.Experimental
  public static @NotNull @Nls String dumbModeMessage(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                      @NotNull @PropertyKey(resourceBundle = BUNDLE) String lightModeKey,
                                                      Object @NotNull ... params) {
    return message(IdeProductMode.isLight() ? lightModeKey : key, params);
  }
}
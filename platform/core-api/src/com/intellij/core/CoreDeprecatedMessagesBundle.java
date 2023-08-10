// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

/**
 * This bundle stores messages from core part of IntelliJ platform which aren't used from the platform code anymore but are still used from
 * some external plugins. It isn't supposed to be used by plugins directly.
 */
@ApiStatus.Internal
public class CoreDeprecatedMessagesBundle extends AbstractBundle {
  private static final @NonNls String BUNDLE = "messages.CoreDeprecatedMessagesBundle";
  private static final CoreDeprecatedMessagesBundle INSTANCE = new CoreDeprecatedMessagesBundle();

  private CoreDeprecatedMessagesBundle() {
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

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

/**
 * This bundle stores messages from IDE part of IntelliJ platform which aren't used from the platform code anymore but are still used from
 * some external plugins. It isn't supposed to be used by plugins directly.
 */
@ApiStatus.Internal
public final class IdeDeprecatedMessagesBundle {
  private static final @NonNls String BUNDLE = "messages.IdeDeprecatedMessagesBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(IdeDeprecatedMessagesBundle.class, BUNDLE);

  private IdeDeprecatedMessagesBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                              Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

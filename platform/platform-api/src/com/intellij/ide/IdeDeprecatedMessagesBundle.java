// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

/**
 * This bundle stores messages from IDE part of IntelliJ platform which aren't used from the platform code anymore but are still used from
 * some external plugins. It isn't supposed to be used by plugins directly.
 */
@ApiStatus.Internal
public class IdeDeprecatedMessagesBundle extends AbstractBundle {
  @NonNls private static final String BUNDLE = "messages.IdeDeprecatedMessagesBundle";
  private static final IdeDeprecatedMessagesBundle INSTANCE = new IdeDeprecatedMessagesBundle();

  private IdeDeprecatedMessagesBundle() {
    super(BUNDLE);
  }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                     Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}

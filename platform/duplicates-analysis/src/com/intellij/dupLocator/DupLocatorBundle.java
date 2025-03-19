package com.intellij.dupLocator;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.*;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class DupLocatorBundle {
  private static final @NonNls String BUNDLE = "messages.DupLocatorBundle";

  private static final DynamicBundle INSTANCE = new DynamicBundle(DupLocatorBundle.class, BUNDLE);

  private DupLocatorBundle() {
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
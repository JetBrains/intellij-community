package com.intellij.dupLocator;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class DupLocatorBundle extends DynamicBundle {
  @NonNls private static final String BUNDLE = "messages.DupLocatorBundle";

  private static final DupLocatorBundle INSTANCE = new DupLocatorBundle();

  private DupLocatorBundle() {
    super(BUNDLE);
  }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
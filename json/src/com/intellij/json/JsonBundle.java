package com.intellij.json;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class JsonBundle extends DynamicBundle {
  @NonNls public static final String BUNDLE = "com.intellij.json.JsonBundle";
  private static final JsonBundle INSTANCE = new JsonBundle();

  private JsonBundle() {
    super(BUNDLE);
  }

  @NotNull
  public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }
}
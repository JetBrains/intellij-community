package org.intellij.lang.regexp;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RegExpPropertiesProvider {
  boolean isValidCategory(@NotNull String category);
  @Nullable
  String getPropertyDescription(@Nullable final String name);
  @NotNull
  String[][] getAllKnownProperties();
}

package com.intellij.database.extractors;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface DisplayType {
  @NotNull @Nls String getName();
}

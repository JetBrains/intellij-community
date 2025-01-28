package com.intellij.database.extractors;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public interface DisplayType {
  @NotNull @Nls String getName();
}

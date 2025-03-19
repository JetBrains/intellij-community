package com.intellij.database.util;

import org.jetbrains.annotations.NotNull;

public interface AppendableWithRead extends Appendable {
  @NotNull String getString();
}

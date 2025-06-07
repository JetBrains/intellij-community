package com.intellij.database.data.types;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConversionGraph {
  @Nullable
  Function<Object, Object> getConverter(@NotNull ConversionPoint<?> startPoint, @NotNull ConversionPoint<?> endPoint);
}

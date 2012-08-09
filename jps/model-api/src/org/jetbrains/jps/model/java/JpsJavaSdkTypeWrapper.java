package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author nik
 */
public interface JpsJavaSdkTypeWrapper {
  @Nullable
  String getJavaSdkName(@NotNull JpsElement properties);
}

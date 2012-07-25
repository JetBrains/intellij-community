package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsSdkProperties;

/**
 * @author nik
 */
public interface JpsJavaSdkTypeWrapper {
  @Nullable
  String getJavaSdkName(@NotNull JpsSdkProperties properties);
}

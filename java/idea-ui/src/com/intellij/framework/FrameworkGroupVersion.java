package com.intellij.framework;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface FrameworkGroupVersion {
  @NotNull String getId();
  @NotNull String getPresentableName();
}

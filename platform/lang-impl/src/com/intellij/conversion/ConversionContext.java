package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author nik
 */
public interface ConversionContext {
  @NotNull
  File getProjectBaseDir();
}

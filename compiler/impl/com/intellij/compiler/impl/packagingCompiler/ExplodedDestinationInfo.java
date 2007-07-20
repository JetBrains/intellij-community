/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.impl.packagingCompiler;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class ExplodedDestinationInfo extends DestinationInfo {
  public ExplodedDestinationInfo(final String outputPath, final @Nullable VirtualFile outputFile) {
    super(outputPath, outputFile, outputPath);
  }

  public String toString() {
    return getOutputPath();
  }
}

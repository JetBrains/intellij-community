/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import org.jetbrains.annotations.Nullable;

public interface OutputToSourceMapping {
  @Nullable
  String getSourcePath(String outputPath);
}

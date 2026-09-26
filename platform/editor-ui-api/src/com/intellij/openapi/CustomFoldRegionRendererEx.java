// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.editor.CustomFoldRegionRenderer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface CustomFoldRegionRendererEx extends CustomFoldRegionRenderer {
  int getMinimumHeightInPixels();
}

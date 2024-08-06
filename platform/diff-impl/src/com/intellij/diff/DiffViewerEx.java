// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;

public interface DiffViewerEx extends FrameDiffTool.DiffViewer {
  default @Nullable PrevNextDifferenceIterable getDifferenceIterable() {
    return null;
  }

  default @Nullable Navigatable getNavigatable() {
    return null;
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.GutterMark;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface MergeableGutterIconRenderer extends GutterMark, Comparable<MergeableGutterIconRenderer> {
  int getWeight();

  @Override
  default int compareTo(MergeableGutterIconRenderer other) {
    return -Integer.compare(this.getWeight(), other.getWeight());
  }
}

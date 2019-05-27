// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.markup.TextAttributes;

public final class HighlightedRegion {
  public int startOffset;
  public int endOffset;
  public TextAttributes textAttributes;

  public HighlightedRegion(int startOffset, int endOffset, TextAttributes textAttributes) {
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    this.textAttributes = textAttributes;
  }
}
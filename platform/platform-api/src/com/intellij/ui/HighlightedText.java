// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class HighlightedText {
  private final @Nls StringBuilder myBuffer;
  private final List<HighlightedRegion> myHighlightedRegions = new ArrayList<>();

  public HighlightedText() {
    myBuffer = new StringBuilder();
  }

  public void appendText(@Nls String text, TextAttributes attributes) {
    int startOffset = myBuffer.length();
    myBuffer.append(text);
    if (attributes != null) {
      myHighlightedRegions.add(new HighlightedRegion(startOffset, myBuffer.length(), attributes));
    }
  }

  public void appendText(char[] text, TextAttributes attributes) {
    int startOffset = myBuffer.length();
    myBuffer.append(text);
    if (attributes != null) {
      myHighlightedRegions.add(new HighlightedRegion(startOffset, myBuffer.length(), attributes));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof HighlightedText highlightedText)) return false;

    return StringUtil.equals(myBuffer, highlightedText.myBuffer) &&
           myHighlightedRegions.equals(highlightedText.myHighlightedRegions);
  }

  public @NotNull @Nls String getText() {
    return myBuffer.toString();
  }

  public void applyToComponent(HighlightableComponent renderer) {
    renderer.setText(myBuffer.toString());
    for (HighlightedRegion info : myHighlightedRegions) {
      renderer.addHighlighter(info.startOffset, info.endOffset, info.textAttributes);
    }
  }
}

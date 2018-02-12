/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class HighlightedText {
  private final StringBuilder myBuffer;
  private final List<HighlightedRegion> myHighlightedRegions = new ArrayList<>();

  public HighlightedText() {
    myBuffer = new StringBuilder();
  }

  public void appendText(String text, TextAttributes attributes) {
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

  public boolean equals(Object o) {
    if (!(o instanceof HighlightedText)) return false;

    HighlightedText highlightedText = (HighlightedText)o;

    return StringUtil.equals(myBuffer, highlightedText.myBuffer) &&
           myHighlightedRegions.equals(highlightedText.myHighlightedRegions);
  }

  public String getText() {
    return myBuffer.toString();
  }

  public void applyToComponent(HighlightableComponent renderer) {
    renderer.setText(myBuffer.toString());
    for (HighlightedRegion info : myHighlightedRegions) {
      renderer.addHighlighter(info.startOffset, info.endOffset, info.textAttributes);
    }
  }
}

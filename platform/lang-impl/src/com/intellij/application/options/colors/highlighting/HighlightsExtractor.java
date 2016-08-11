/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.application.options.colors.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HighlightsExtractor {

  private final Map<String,TextAttributesKey> myTags;
  private int myStartOffset;
  private int myEndOffset;

  private int mySkippedLen;
  private int myIndex;
  private boolean myIsOpeningTag;

  private List<TextRange> mySkipped = new ArrayList<>();

  public HighlightsExtractor(@Nullable Map<String, TextAttributesKey> tags) {
    myTags = tags;
  }

  public String extractHighlights(String text, List<HighlightData> highlights) {
    mySkipped.clear();
    if (myTags == null || myTags.isEmpty()) return text;
    resetIndices();
    Stack<HighlightData> highlightsStack = new Stack<>();
    while (true) {
      String tagName = findTagName(text);
      if (tagName == null || myIndex < 0) break;
      if (myTags.containsKey(tagName)) {
        if (myIsOpeningTag) {
          mySkippedLen += tagName.length() + 2;
          HighlightData highlightData = new HighlightData(myStartOffset - mySkippedLen, myTags.get(tagName));
          highlightsStack.push(highlightData);
        } else {
          HighlightData highlightData = highlightsStack.pop();
          highlightData.setEndOffset(myEndOffset - mySkippedLen);
          mySkippedLen += tagName.length() + 3;
          highlights.add(highlightData);
        }
      }
    }

    return cutDefinedTags(text);
  }

  private String findTagName(String text) {
    myIsOpeningTag = true;
    int openTag = text.indexOf('<', myIndex);
    if (openTag == -1) {
      return null;
    }
    while (text.charAt(openTag + 1) == '<') {
      openTag++;
    }
    if (text.charAt(openTag + 1) == '/') {
      myIsOpeningTag = false;
      openTag++;
    }
    if (!isValidTagFirstChar(text.charAt(openTag + 1))) {
      myIndex = openTag + 1;
      return "";
    }

    int closeTag = text.indexOf('>', openTag + 1);
    if (closeTag == -1) return null;
    int i = text.indexOf('<', openTag + 1);
    if (i != -1 && i < closeTag) {
      myIndex = i;
      return "";
    }
    final String tagName = text.substring(openTag + 1, closeTag);

    if (myIsOpeningTag) {
      myStartOffset = openTag + tagName.length() + 2;
      if (myTags.containsKey(tagName)) {
        mySkipped.add(TextRange.from(openTag, tagName.length() + 2));
      }
    } else {
      myEndOffset = openTag - 1;
      if (myTags.containsKey(tagName)) {
        mySkipped.add(TextRange.from(openTag - 1, tagName.length() + 3));
      }
    }
    myIndex = Math.max(myStartOffset, myEndOffset + 1);
    return tagName;
  }
  
  private static boolean isValidTagFirstChar(char c) {
    return Character.isLetter(c) || c == '_';
  }

  private String cutDefinedTags(String text) {
    StringBuilder builder = new StringBuilder(text);
    for (int i = mySkipped.size() - 1; i >= 0; i--) {
      TextRange range = mySkipped.get(i);
      builder.delete(range.getStartOffset(), range.getEndOffset());
    }
    return builder.toString();
  }

  private void resetIndices() {
    myIndex = 0;
    myStartOffset = 0;
    myEndOffset = 0;
    mySkippedLen = 0;
  }
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
 * @author Yura Cangea
 */
package com.intellij.application.options.colors.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
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
  private static final HighlightData[] EMPTY_DATA = new HighlightData[0];

  public HighlightsExtractor(@Nullable Map<String, TextAttributesKey> tags) {
    myTags = tags;
  }

  public HighlightData[] extractHighlights(String text) {
    if (myTags == null || myTags.isEmpty()) return EMPTY_DATA;
    resetIndices();
    List<HighlightData> highlights = new ArrayList<HighlightData>();
    Stack<HighlightData> highlightsStack = new Stack<HighlightData>();
    while (true) {
      String tagName = findTagName(text);
      if (tagName == null) break;
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

    return highlights.toArray(new HighlightData[highlights.size()]);
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
    final String tagName = text.substring(openTag + 1, closeTag);

    if (myIsOpeningTag) {
      myStartOffset = openTag + tagName.length() + 2;
    } else {
      myEndOffset = openTag - 1;
    }
    myIndex = Math.max(myStartOffset, myEndOffset + 1);
    return tagName;
  }
  
  private static boolean isValidTagFirstChar(char c) {
    return Character.isLetter(c) || c == '_';
  }

  public String cutDefinedTags(String text) {
    if (myTags == null || myTags.isEmpty()) return text;

    StringBuffer sb = new StringBuffer();
    int index = 0;
    while (true) {
      int from = text.indexOf('<', index);
      if (from == -1) {
        sb.append(text.substring(index, text.length()));
        break;
      }
      while (text.charAt(from+1) == '<') {
        from++;
      }
      int to = text.indexOf('>', from+1);
      if (to == -1) {
        sb.append(text.substring(index, text.length()));
        break;
      }
      int tagNameStart = from + 1;
      if (text.charAt(tagNameStart) == '/') {
        tagNameStart ++;
      }

      if (isValidTagFirstChar(text.charAt(tagNameStart))) {
        String tag;
        tag = text.substring(tagNameStart, to);
        if (myTags.containsKey(tag)) {
          sb.append(text.substring(index, from));
          index = to + 1;
          continue;
        }
      }
      else {
        to = from;
      }
      sb.append(text.substring(index, to + 1));
      index = to + 1;
    }

    return sb.toString();
  }

  private void resetIndices() {
    myIndex = 0;
    myStartOffset = 0;
    myEndOffset = 0;
    mySkippedLen = 0;
  }
}

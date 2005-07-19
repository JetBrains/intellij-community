/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.util.text.CharArrayUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class ChunkExtractor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.ChunkExtractor");

  private final PsiElement myElement;
  private final Document myDocument;
  private final int myLineNumber;
  private final int myColumnNumber;

  private final List<RangeMarker> myRangeMarkers;
  private final EditorColorsScheme myColorsScheme;

  public ChunkExtractor(final PsiElement element,
                        final List<RangeMarker> rangeMarkers) {

    myElement = element;
    myRangeMarkers = rangeMarkers;
    myColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();

    final int absoluteStartOffset =  getStartOffset(myRangeMarkers);

    myDocument = PsiDocumentManager.getInstance(myElement.getProject()).getDocument(myElement.getContainingFile());
    myLineNumber = myDocument.getLineNumber(absoluteStartOffset);
    myColumnNumber = absoluteStartOffset - myDocument.getLineStartOffset(myLineNumber);
  }

  private int getStartOffset(final List<RangeMarker> rangeMarkers) {
    LOG.assertTrue(rangeMarkers.size() > 0);
    int minStart = Integer.MAX_VALUE;
    for (RangeMarker rangeMarker : rangeMarkers) {
      final int startOffset = rangeMarker.getStartOffset();
      if (startOffset < minStart) minStart = startOffset;
    }

    return minStart;
  }

  public TextChunk[] extractChunks() {
    final int lineStartOffset = myDocument.getLineStartOffset(myLineNumber);
    final int lineEndOffset = myDocument.getLineEndOffset(myLineNumber);
    final FileType fileType = myElement.getContainingFile().getFileType();
    return createTextChunks(myDocument.getCharsSequence(), fileType.getHighlighter(myElement.getProject()), lineStartOffset, lineEndOffset);
  }

  private TextChunk[] createTextChunks(final CharSequence chars,
    SyntaxHighlighter highlighter,
    int start,
    int end) {
    LOG.assertTrue(start <= end);
    List<TextChunk> result = new ArrayList<TextChunk>();

    appendPrefix(result);

    Lexer lexer = highlighter.getHighlightingLexer();
    lexer.start(CharArrayUtil.fromSequence(chars));

    for (int offset = start; offset < end; offset++) {
      if (chars.charAt(offset) == '\n') {
        end = offset;
        break;
      }
    }

    boolean isBeginning = true;

    while (lexer.getTokenType() != null) {
      try {
        int hiStart = lexer.getTokenStart();
        int hiEnd = lexer.getTokenEnd();

        if (hiStart >= end) break;

        hiStart = Math.max(hiStart, start);
        hiEnd = Math.min(hiEnd, end);
        if (hiStart >= hiEnd) { continue; }

        String text = chars.subSequence(hiStart, hiEnd).toString();
        if (isBeginning && text.trim().length() == 0) continue;
        isBeginning = false;
        IElementType tokenType = lexer.getTokenType();
        TextAttributesKey[] tokenHighlights = highlighter.getTokenHighlights(tokenType);

        RangeMarker intersectionMarker = getIntersectingMarker(hiStart, hiEnd);
        if (intersectionMarker != null) {
          processIntersectingRange(chars, hiStart, hiEnd, tokenHighlights, result, intersectionMarker);
        }
        else {
          result.add(new TextChunk(convertAttributes(tokenHighlights), text));
        }
      }
      finally {
        lexer.advance();
      }
    }

    return result.toArray(new TextChunk[result.size()]);
  }

  private RangeMarker getIntersectingMarker(int hiStart, int hiEnd) {
    for (int i = 0; i < myRangeMarkers.size(); i++) {
      RangeMarker marker = myRangeMarkers.get(i);
      if (marker.isValid() && rangeIntersect(hiStart, hiEnd, marker.getStartOffset(), marker.getEndOffset())) return marker;
    }
    return null;
  }

  private void processIntersectingRange(CharSequence chars,
    int hiStart,
    int hiEnd,
    TextAttributesKey[] tokenHighlights,
    List<TextChunk> result,
    RangeMarker rangeMarker) {
    int usageStart = rangeMarker.getStartOffset();
    int usageEnd = rangeMarker.getEndOffset();

    TextAttributes originalAttrs = convertAttributes(tokenHighlights);
    addChunk(chars, hiStart, Math.max(hiStart, usageStart), originalAttrs, false, result);
    addChunk(chars, Math.max(hiStart, usageStart), Math.min(hiEnd, usageEnd), originalAttrs, true, result);
    addChunk(chars, Math.min(hiEnd, usageEnd), hiEnd, originalAttrs, false, result);
  }

  private static void addChunk(CharSequence chars, int start, int end, TextAttributes originalAttrs, boolean bold, List<TextChunk> result) {
    if (start >= end) return;
    String rText = chars.subSequence(start, end).toString();
    TextAttributes attrs = bold
        ? TextAttributes.merge(originalAttrs, new TextAttributes(null, null, null, null, Font.BOLD))
        : originalAttrs;
    result.add(new TextChunk(attrs, rText));
  }

  private static boolean rangeIntersect(int s1, int e1, int s2, int e2) {
    return s2 < s1 && s1 < e2 || s2 < e1 && e1 < e2
                          || s1 < s2 && s2 < e1 || s1 < e2 && e2 < e1
    || s1 == s2 && e1 == e2;
  }

  private TextAttributes convertAttributes(TextAttributesKey[] keys) {
    TextAttributes attrs = myColorsScheme.getAttributes(HighlighterColors.TEXT);

    for (TextAttributesKey key : keys) {
      TextAttributes attrs2 = myColorsScheme.getAttributes(key);
      if (attrs2 != null) {
        attrs = TextAttributes.merge(attrs, attrs2);
      }
    }

    attrs = attrs.clone();
    attrs.setFontType(Font.PLAIN);
    return attrs;
  }

  private void appendPrefix(List<TextChunk> result) {
    StringBuffer buffer = new StringBuffer("(");
    buffer.append(myLineNumber + 1);
    buffer.append(", ");
    buffer.append(myColumnNumber + 1);
    buffer.append(") ");
    TextChunk prefixChunk = new TextChunk(myColorsScheme.getAttributes(UsageTreeColors.USAGE_LOCATION), buffer.toString());
    result.add(prefixChunk);
  }



}

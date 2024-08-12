// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.RangeMarkerImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ConsoleTokenUtil {
  private static final char BACKSPACE = '\b';
  private static final Key<ConsoleViewContentType> CONTENT_TYPE = Key.create("ConsoleViewContentType");
  private static final Key<Boolean> USER_INPUT_SENT = Key.create("USER_INPUT_SENT");
  static final Key<Boolean> MANUAL_HYPERLINK = Key.create("MANUAL_HYPERLINK");

  // convert all "a\bc" sequences to "c", not crossing the line boundaries in the process
  private static void normalizeBackspaceCharacters(@NotNull StringBuilder text) {
    int ind = StringUtil.indexOf(text, BACKSPACE);
    if (ind < 0) {
      return;
    }
    int guardLength = 0;
    int newLength = 0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      boolean append;
      if (ch == BACKSPACE) {
        assert guardLength <= newLength;
        if (guardLength == newLength) {
          // Backspace is the first char in a new line:
          // Keep backspace at the first line (guardLength == 0) as it might be in the middle of the actual line,
          // handle it later (see getBackspacePrefixLength).
          // Otherwise (for non-first lines), skip backspace as it can't be interpreted if located right after line ending.
          append = guardLength == 0;
        }
        else {
          append = text.charAt(newLength - 1) == BACKSPACE;
          if (!append) {
            newLength--; // interpret \b: delete prev char
          }
        }
      }
      else {
        append = true;
      }
      if (append) {
        text.setCharAt(newLength, ch);
        newLength++;
        if (ch == '\r' || ch == '\n') guardLength = newLength;
      }
    }
    text.setLength(newLength);
  }

  static int evaluateBackspacesInTokens(@NotNull List<TokenBuffer.TokenInfo> source,
                                        int sourceStartIndex,
                                        @NotNull List<? super TokenBuffer.TokenInfo> dest) {
    int backspacesFromNextToken = 0;
    for (int i = source.size() - 1; i >= sourceStartIndex; i--) {
      TokenBuffer.TokenInfo token = source.get(i);
      TokenBuffer.TokenInfo newToken;
      if (StringUtil.containsChar(token.getText(), BACKSPACE) || backspacesFromNextToken > 0) {
        StringBuilder tokenTextBuilder = new StringBuilder(token.getText().length() + backspacesFromNextToken);
        tokenTextBuilder.append(token.getText());
        StringUtil.repeatSymbol(tokenTextBuilder, BACKSPACE, backspacesFromNextToken);
        normalizeBackspaceCharacters(tokenTextBuilder);
        backspacesFromNextToken = getBackspacePrefixLength(tokenTextBuilder);
        String newText = tokenTextBuilder.substring(backspacesFromNextToken);
        newToken = new TokenBuffer.TokenInfo(token.contentType, newText, token.getHyperlinkInfo());
      }
      else {
        newToken = token;
      }
      dest.add(newToken);
    }
    Collections.reverse(dest);
    return backspacesFromNextToken;
  }

  private static int getBackspacePrefixLength(@NotNull CharSequence text) {
    return StringUtil.countChars(text, BACKSPACE, 0, true);
  }

  public static @Nullable ConsoleViewContentType getTokenType(@NotNull RangeMarker m) {
    return m.getUserData(CONTENT_TYPE);
  }

  public static void saveTokenType(@NotNull RangeMarker m, @NotNull ConsoleViewContentType contentType) {
    m.putUserData(CONTENT_TYPE, contentType);
  }

  // finds range marker the [offset..offset+1) belongs to
  static RangeMarker findTokenMarker(@NotNull Editor editor, @NotNull Project project, int offset) {
    ThreadingAssertions.assertEventDispatchThread();
    RangeMarker[] marker = new RangeMarker[1];
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(editor.getDocument(), project, true);
    model.processRangeHighlightersOverlappingWith(offset, offset, m->{
      if (getTokenType(m) == null || m.getStartOffset() > offset || offset + 1 > m.getEndOffset()) return true;
      marker[0] = m;
      return false;
    });

    return marker[0];
  }

  static void createTokenRangeHighlighter(@NotNull Editor editor,
                                          @NotNull Project project,
                                          @NotNull ConsoleViewContentType contentType,
                                          int startOffset,
                                          int endOffset,
                                          boolean mergeWithThePreviousSameTypeToken) {
    ThreadingAssertions.assertEventDispatchThread();
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(editor.getDocument(), project, true);
    int layer = HighlighterLayer.SYNTAX + 1; // make custom filters able to draw their text attributes over the default ones
    if (mergeWithThePreviousSameTypeToken && startOffset > 0) {
      RangeMarker prevMarker = findTokenMarker(editor, project, startOffset - 1);
      ConsoleViewContentType prevMarkerType = prevMarker == null ? null : getTokenType(prevMarker);
      int prevMarkerEndOffset = prevMarkerType == null ? -1 : prevMarker.getEndOffset();
      if (contentType.equals(prevMarkerType) &&
          prevMarkerEndOffset >= 0 &&
          prevMarkerEndOffset < editor.getDocument().getTextLength() &&
          // must not merge tokens with end line because user input should be separated by new lines
          editor.getDocument().getCharsSequence().charAt(prevMarkerEndOffset - 1) != '\n') {
        startOffset = prevMarker.getStartOffset();
        prevMarker.dispose();
      }
    }
    model.addRangeHighlighterAndChangeAttributes(
      contentType.getAttributesKey(), startOffset, endOffset, layer, HighlighterTargetArea.EXACT_RANGE, false,
      rm -> {
        // fallback for contentTypes that provides only attributes
        if (rm.getTextAttributesKey() == null) {
          rm.setTextAttributes(contentType.getAttributes());
        }
        saveTokenType(rm, contentType);
      });
  }

  static void updateAllTokenTextAttributes(@NotNull Editor editor, @NotNull Project project) {
    MarkupModel model = DocumentMarkupModel.forDocument(editor.getDocument(), project, false);
    for (RangeHighlighter tokenMarker : model.getAllHighlighters()) {
      ConsoleViewContentType contentType = getTokenType(tokenMarker);
      if (contentType != null && contentType.getAttributesKey() == null && tokenMarker instanceof RangeHighlighterEx) {
        ((RangeHighlighterEx)tokenMarker).setTextAttributes(contentType.getAttributes());
      }
    }
  }

  static @NotNull CharSequence computeTextToSend(@NotNull Editor editor, @NotNull Project project) {
    StringBuilder textToSend = new StringBuilder();
    // compute text input from the console contents:
    // all range markers beginning from the caret offset backwards, marked as user input and not marked as already sent
    for (RangeMarker marker = findTokenMarker(editor, project, editor.getCaretModel().getOffset() - 1);
         marker != null;
         marker = ((RangeMarkerImpl)marker).findRangeMarkerBefore()) {
      ConsoleViewContentType tokenType = getTokenType(marker);
      if (tokenType != null) {
        if (tokenType != ConsoleViewContentType.USER_INPUT || marker.getUserData(USER_INPUT_SENT) == Boolean.TRUE) {
          continue;
        }
        marker.putUserData(USER_INPUT_SENT, true);
        textToSend.insert(0, marker.getDocument().getText(marker.getTextRange()));
      }
    }
    return textToSend;
  }

  static void highlightTokenTextAttributes(@NotNull Editor editor,
                                           @NotNull Project project,
                                           @NotNull List<TokenBuffer.TokenInfo> tokens,
                                           @NotNull EditorHyperlinkSupport hyperlinks,
                                           @NotNull Collection<? super ConsoleViewContentType> contentTypes,
                                           @NotNull List<? super Pair<String, ConsoleViewContentType>> contents) {
    // add token information as range markers
    // start from the end because portion of the text can be stripped from the document beginning because of a cycle buffer
    int offset = editor.getDocument().getTextLength();
    int tokenLength = 0;
    for (int i = tokens.size() - 1; i >= 0; i--) {
      TokenBuffer.TokenInfo token = tokens.get(i);
      contentTypes.add(token.contentType);
      contents.add(new Pair<>(token.getText(), token.contentType));
      tokenLength += token.length();
      TokenBuffer.TokenInfo prevToken = i == 0 ? null : tokens.get(i - 1);
      if (prevToken != null && token.contentType == prevToken.contentType && token.getHyperlinkInfo() == prevToken.getHyperlinkInfo()) {
        // do not create highlighter yet because can merge previous token with the current
        continue;
      }
      int start = Math.max(0, offset - tokenLength);
      if (start == offset) {
        continue;
      }
      HyperlinkInfo info = token.getHyperlinkInfo();
      if (info != null) {
        hyperlinks.createHyperlink(start, offset, null, info).putUserData(MANUAL_HYPERLINK, true);
      }
      createTokenRangeHighlighter(editor, project, token.contentType, start, offset, false);
      offset = start;
      tokenLength = 0;
    }
  }
}

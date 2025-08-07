// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.comments;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
@ApiStatus.Internal
public class DocCommentLineDataBuilder extends CommentLineDataBuilder {
  private final boolean myAlignDocComments;
  private final PsiElement myDocComment;
  private final CommonCodeStyleSettings.IndentOptions myIndentOptions;

  public DocCommentLineDataBuilder(@NotNull PsiElement docComment,
                                   boolean alignDocComments,
                                   @NotNull CommonCodeStyleSettings settings) {
    super(settings.getRootSettings());
    myDocComment = docComment;
    myAlignDocComments = alignDocComments;
    myIndentOptions = settings.getIndentOptions() != null ? settings.getIndentOptions() : new CommonCodeStyleSettings.IndentOptions();
  }

  @Override
  public List<CommentLineData> getLines() {
    CharSequence docChars = myDocComment.getNode().getChars();
    List<CommentLineData> lines  = new ArrayList<>();
    int lineStart = 0;
    boolean isTagLine = false;
    for (int i = 0; i < docChars.length(); i ++) {
      char c = docChars.charAt(i);
      switch (c) {
        case '\n' -> {
          if (lineStart < i) {
            CommentLineData lineData = createLineDataFromSequence(docChars, lineStart, i, isTagLine);
            isTagLine = lineData.isTagLine();
            lines.add(lineData);
          }
          lineStart = i + 1;
        }
        case '\r' -> {}
      }
    }
    if (lineStart < docChars.length()) {
      lines.add(createLineDataFromSequence(docChars, lineStart, docChars.length(), isTagLine));
    }
    return lines;
  }

  private CommentLineData createLineDataFromSequence(CharSequence charSequence, int start, int end, boolean isTagLine) {
    String line = new String(CharArrayUtil.fromSequence(charSequence, start, end));
    CommentLineData lineData = parseLine(line);
    lineData.setTagLine(isTagLine);
    return lineData;
  }

  @Override
  public @NotNull CommentLineData parseLine(@NotNull String line) {
    DocCommentLineData lineData = new DocCommentLineData(line);
    lineData.commentOffset = nextNonWhitespace(line, 0);
    if (lineData.commentOffset >= 0) {
      if (line.charAt(lineData.commentOffset) == '*') lineData.commentOffset++;
      int next = nextNonWhitespace(line, lineData.commentOffset);
      if (next >= 0) {
        char firstChar = line.charAt(next);
        if (firstChar == '@') {
          lineData.tagStartOffset = next;
          next = nextWhitespace(line, next + 1);
          lineData.tagEndOffset = next >= 0 ? next : line.length();
          if (lineData.tagEndOffset > lineData.tagStartOffset) lineData.tagName = line.substring(lineData.tagStartOffset, lineData.tagEndOffset);
          if (next >= 0) {
            if ("@param".equals(lineData.tagName)) {
              next = nextNonWhitespace(line, next + 1);
              if (next >= 0) {
                boolean isAtParamName = line.charAt(next) == '$';
                next = nextWhitespace(line, next);
                if (!isAtParamName && next >= 0) {
                  next = skipNextWord(line, next);
                }
              }
            }
            else if ("@return".equals(lineData.tagName) || "@throws".equals(lineData.tagName)) {
              next = skipNextWord(line, next);
            }
          }
        }
        else {
          if (Character.isLetter(firstChar)) lineData.startsWithLetter = true;
        }
        if (next >= 0) {
          next = nextNonWhitespace(line, next);
          lineData.textStartOffset = next >= 0 ? next : -1;
          collectInlineTagsRanges(lineData, line);
        }
      }
    }
    return lineData;
  }

  private static void collectInlineTagsRanges(@NotNull CommentLineData lineData, @NotNull String line) {
    if (lineData.textStartOffset < 0) return;
    int inlineTagStart = -1;
    for (int i = lineData.textStartOffset; i < line.length(); i ++) {
      char c = line.charAt(i);
      switch (c) {
        case '{' -> {
          int j = i + 1;
          if (j < line.length() && line.charAt(j) == '@') {
            inlineTagStart = i;
          }
        }
        case '}' -> {
          if (inlineTagStart >= 0) {
            TextRange tagRange = new TextRange(findLinkDescriptionStart(line, inlineTagStart, lineData.textStartOffset), i);
            lineData.addUnbreakableRange(tagRange);
          }
          inlineTagStart = -1;
        }
      }
    }
  }

  private static int findLinkDescriptionStart(String line, int from, int textStartOffset) {
    if (from <= textStartOffset || line.charAt(from - 1) != ']') {
      return from;
    }
    for (int i = from; i >= textStartOffset; i--) {
      char c = line.charAt(i);
      if (c == '[') {
        return i;
      }
    }
    return from;
  }

  private class DocCommentLineData extends CommentLineData {
    int tagStartOffset = -1;
    int tagEndOffset = -1;
    String tagName = "";
    private boolean isTagLine = false;

    DocCommentLineData(@NotNull String line) {
      super(line);
    }

    @Override
    public boolean isTagLine() {
      return tagStartOffset >= 0 || isTagLine;
    }

    @Override
    public void setTagLine(boolean tagLine) {
      this.isTagLine = tagLine;
    }

    @Override
    public @NotNull String getLinePrefix() {
      StringBuilder prefixBuilder = new StringBuilder();
      if (this.commentOffset >= 0) {
        prefixBuilder.append(this.line, 0, this.commentOffset);
        if (this.textStartOffset > this.commentOffset) {
          if (myAlignDocComments) {
            StringUtil.repeatSymbol(prefixBuilder, ' ', this.textStartOffset - this.commentOffset - 1);
          }
          else if (this.isTagLine()) {
            StringUtil.repeatSymbol(prefixBuilder, ' ', myIndentOptions.CONTINUATION_INDENT_SIZE);
          }
        }
      }
      return prefixBuilder.toString();
    }

    @Override
    protected int getTabSize() {
      return myIndentOptions.TAB_SIZE;
    }
  }
}

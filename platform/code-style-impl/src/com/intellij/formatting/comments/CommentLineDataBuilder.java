// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.comments;

import com.intellij.formatting.FormatterTagHandler;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Rustam Vishnyakov
 */
@ApiStatus.Internal
public abstract class CommentLineDataBuilder extends FormatterTagHandler {
  private static final String DOC_WHITESPACE = "\t ";

  public CommentLineDataBuilder(CodeStyleSettings settings) {
    super(settings);
  }

  public abstract @Nullable List<CommentLineData> getLines();

  public abstract @NotNull CommentLineData parseLine(@NotNull String line);

  protected static int nextNonWhitespace(String str, int from) {
    int result = CharArrayUtil.shiftForward(str, from, DOC_WHITESPACE);
    return result == str.length() ? -1 : result;
  }

  protected static int nextWhitespace(String str, int from) {
    return StringUtil.indexOfAny(str, DOC_WHITESPACE, from, str.length());
  }

  protected static int skipNextWord(String str, int from) {
    int next = nextNonWhitespace(str, from);
    if (next >= 0) next = nextWhitespace(str, next);
    return next;
  }
}

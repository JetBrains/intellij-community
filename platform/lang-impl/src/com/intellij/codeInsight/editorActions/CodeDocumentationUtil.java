// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.DocCommentSettings;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class CodeDocumentationUtil {

  private CodeDocumentationUtil() {
  }

  public static String createDocCommentLine(String lineData, PsiFile file, CodeDocumentationAwareCommenter commenter) {
    DocCommentSettings settings = CodeStyleManager.getInstance(file.getProject()).getDocCommentSettings(file);
    return createLine(lineData, commenter, settings);
  }

  private static @NotNull String createLine(String lineData, CodeDocumentationAwareCommenter commenter, DocCommentSettings settings) {
    if (!settings.isLeadingAsteriskEnabled()) {
      return " " + lineData + " ";
    }
    else {
      if (lineData.length() == 0) {
        return commenter.getDocumentationCommentLinePrefix() + " ";
      }
      else {
        return commenter.getDocumentationCommentLinePrefix() + " " + lineData + " ";
      }

    }
  }

  /**
   * Utility method that does the following:
   * <pre>
   * <ol>
   *   <li>Checks if target document line that contains given offset starts with '*';</li>
   *   <li>Returns document text located between the '*' and first non-white space symbols after it if the check above is successful;</li>
   * </ol>
   </pre>
   *
   * @param document    target document
   * @param offset      target offset that identifies line to check and max offset to use during scanning
   */
  public static @Nullable String getIndentInsideJavadoc(@NotNull Document document, int offset) {
    CharSequence text = document.getCharsSequence();
    if (offset >= text.length()) {
      return null;
    }
    int line = document.getLineNumber(offset);
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    int i = CharArrayUtil.shiftForward(text, lineStartOffset, " \t");
    if (i > lineEndOffset || text.charAt(i) != '*') {
      return null;
    }

    int start = i + 1;
    int end = CharArrayUtil.shiftForward(text, start, " \t");
    end = Math.min(end, lineEndOffset);
    return end > start ? text.subSequence(start, end).toString() : "";
  }

  /**
   * Analyzes position at the given offset at the given text and returns information about comments presence and kind there if any.
   *
   * @param file              target file being edited (necessary for language recognition at target offset. Language is necessary
   *                          to get information about specific comment syntax)
   * @param chars             target text
   * @param offset            target offset at the given text
   * @param lineStartOffset   start offset of the line that contains given offset
   * @return                  object that encapsulates information about comments at the given offset at the given text
   */
  public static @NotNull CommentContext tryParseCommentContext(@NotNull PsiFile file, @NotNull CharSequence chars, int offset, int lineStartOffset) {
    Commenter langCommenter = LanguageCommenters.INSTANCE.forLanguage(PsiUtilCore.getLanguageAtOffset(file, offset));
    return tryParseCommentContext(langCommenter, chars, lineStartOffset);
  }

  static CommentContext tryParseCommentContext(@Nullable Commenter langCommenter,
                                               @NotNull CharSequence chars,
                                               int lineStartOffset) {
    final boolean isInsideCommentLikeCode = langCommenter instanceof CodeDocumentationAwareCommenter;
    if (!isInsideCommentLikeCode) {
      return new CommentContext();
    }
    final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)langCommenter;
    int commentStartOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t");

    boolean docStart = commenter.getDocumentationCommentPrefix() != null
                       && CharArrayUtil.regionMatches(chars, commentStartOffset, commenter.getDocumentationCommentPrefix());
    boolean docAsterisk = commenter.getDocumentationCommentLinePrefix() != null
                          && CharArrayUtil.regionMatches(chars, commentStartOffset, commenter.getDocumentationCommentLinePrefix());
    return new CommentContext(commenter, docStart, docAsterisk, commentStartOffset);
  }

  /**
   * Utility class that contains information about current comment context.
   */
  public static final class CommentContext {

    public final CodeDocumentationAwareCommenter commenter;
    public final int                             lineStart;

    /** Indicates position at the line that starts from {@code '/**'} (in java language). */
    public boolean docStart;

    /** Indicates position at the line that starts from {@code '*'} (non-first and non-last javadoc line in java language). */
    public boolean docAsterisk;

    public CommentContext() {
      commenter = null;
      lineStart = 0;
    }

    public CommentContext(CodeDocumentationAwareCommenter commenter,
                          boolean docStart,
                          boolean docAsterisk,
                          int lineStart) {
      this.docStart = docStart;
      this.docAsterisk = docAsterisk;
      this.commenter = commenter;
      this.lineStart = lineStart;
    }
  }
}

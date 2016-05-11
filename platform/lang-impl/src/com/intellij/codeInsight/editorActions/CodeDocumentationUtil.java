/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInsight.editorActions;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CodeDocumentationUtil {

  private CodeDocumentationUtil() {
  }

  public static String createDocCommentLine(String lineData, Project project, CodeDocumentationAwareCommenter commenter) {
    if (!CodeStyleSettingsManager.getSettings(project).JD_LEADING_ASTERISKS_ARE_ENABLED) {
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
   * @return
   */
  @Nullable
  public static String getIndentInsideJavadoc(@NotNull Document document, int offset) {
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
  @NotNull
  public static CommentContext tryParseCommentContext(@NotNull PsiFile file, @NotNull CharSequence chars, int offset, int lineStartOffset) {
    Commenter langCommenter = LanguageCommenters.INSTANCE.forLanguage(PsiUtilCore.getLanguageAtOffset(file, offset));
    return tryParseCommentContext(langCommenter, chars, offset, lineStartOffset);
  }

  static CommentContext tryParseCommentContext(@Nullable Commenter langCommenter,
                                               @NotNull CharSequence chars,
                                               int offset,
                                               int lineStartOffset) {
    final boolean isInsideCommentLikeCode = langCommenter instanceof CodeDocumentationAwareCommenter;
    if (!isInsideCommentLikeCode) {
      return new CommentContext();
    }
    final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)langCommenter;
    int commentStartOffset = CharArrayUtil.shiftForward(chars, lineStartOffset, " \t");

    boolean docStart = commenter.getDocumentationCommentPrefix() != null
                       && CharArrayUtil.regionMatches(chars, commentStartOffset, commenter.getDocumentationCommentPrefix());
    boolean cStyleStart = commenter.getBlockCommentPrefix() != null
                          && CharArrayUtil.regionMatches(chars, commentStartOffset, commenter.getBlockCommentPrefix());
    boolean docAsterisk = commenter.getDocumentationCommentLinePrefix() != null
                          && CharArrayUtil.regionMatches(chars, commentStartOffset, commenter.getDocumentationCommentLinePrefix());
    final int firstNonSpaceInLine = CharArrayUtil.shiftForward(chars, offset, " \t");
    boolean slashSlash = commenter.getLineCommentPrefix() != null
                         && CharArrayUtil.regionMatches(chars, commentStartOffset, commenter.getLineCommentPrefix())
                         && firstNonSpaceInLine < chars.length() && chars.charAt(firstNonSpaceInLine) != '\n';
    return new CommentContext(commenter, docStart, cStyleStart, docAsterisk, slashSlash, commentStartOffset);
  }
  
  /**
   * Utility class that contains information about current comment context.
   */
  public static class CommentContext {

    public final CodeDocumentationAwareCommenter commenter;
    public final int                             lineStart;

    /** Indicates position at the line that starts from {@code '/**'} (in java language). */
    public boolean docStart;

    /** Indicates position at the line that starts from {@code '/*'} (in java language). */
    public boolean cStyleStart;

    /** Indicates position at the line that starts from {@code '*'} (non-first and non-last javadoc line in java language). */
    public boolean docAsterisk;

    /** Indicates position at the line that starts from {@code '//'} (in java language). */
    public boolean slashSlash;

    public CommentContext() {
      commenter = null;
      lineStart = 0;
    }

    public CommentContext(CodeDocumentationAwareCommenter commenter, boolean docStart, boolean cStyleStart, boolean docAsterisk,
                          boolean slashSlash, int lineStart) 
    {
      this.docStart = docStart;
      this.cStyleStart = cStyleStart;
      this.docAsterisk = docAsterisk;
      this.slashSlash = slashSlash;
      this.commenter = commenter;
      this.lineStart = lineStart;
    }
  }
}

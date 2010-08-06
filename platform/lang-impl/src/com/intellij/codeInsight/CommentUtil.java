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

package com.intellij.codeInsight;

import com.intellij.lang.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.Indent;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

public class CommentUtil {
  private CommentUtil() { }

  public static Indent getMinLineIndent(Project project, Document document, int line1, int line2, FileType fileType) {
    CharSequence chars = document.getCharsSequence();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    Indent minIndent = null;
    for (int line = line1; line <= line2; line++) {
      int lineStart = document.getLineStartOffset(line);
      int textStart = CharArrayUtil.shiftForward(chars, lineStart, " \t");
      if (textStart >= document.getTextLength()) {
        textStart = document.getTextLength();
      }
      else {
        char c = chars.charAt(textStart);
        if (c == '\n' || c == '\r') continue; // empty line
      }
      String space = chars.subSequence(lineStart, textStart).toString();
      Indent indent = codeStyleManager.getIndent(space, fileType);
      minIndent = minIndent != null ? indent.min(minIndent) : indent;
    }
    if (minIndent == null && line1 == line2 && line1 < document.getLineCount() - 1) {
      return getMinLineIndent(project, document, line1 + 1, line1 + 1, fileType);
    }
    //if (minIndent == Integer.MAX_VALUE){
    //  minIndent = 0;
    //}
    return minIndent;
  }

  public static boolean isComment(@Nullable final PsiElement element) {
    return element != null && isComment(element.getNode());
  }

  public static boolean isComment(@Nullable final ASTNode node) {
    if (node == null) return false;
    final IElementType type = node.getElementType();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(type.getLanguage());
    return parserDefinition != null && parserDefinition.getCommentTokens().contains(type);
  }

  public static boolean isCommentTextElement(final PsiElement element) {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(element.getLanguage());
    if (commenter instanceof CodeDocumentationAwareCommenterEx) {
      final CodeDocumentationAwareCommenterEx commenterEx = (CodeDocumentationAwareCommenterEx)commenter;
      if (commenterEx.isDocumentationCommentText(element)) return true;
      if (element instanceof PsiComment && commenterEx.isDocumentationComment((PsiComment)element)) return false;
    }

    return isComment(element);
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CodeDocumentationUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class EnterBetweenBracesHandler extends EnterHandlerDelegateAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesHandler");

  @Override
  public Result preprocessEnter(@NotNull final PsiFile file, @NotNull final Editor editor, @NotNull final Ref<Integer> caretOffsetRef, @NotNull final Ref<Integer> caretAdvance,
                                @NotNull final DataContext dataContext, final EditorActionHandler originalHandler) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int caretOffset = caretOffsetRef.get().intValue();
    if (!CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
      return Result.Continue;
    }

    int prevCharOffset = CharArrayUtil.shiftBackward(text, caretOffset - 1, " \t");
    int nextCharOffset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
    
    if (!isValidOffset(prevCharOffset, text) || !isValidOffset(nextCharOffset, text) ||
        !isBracePair(text.charAt(prevCharOffset), text.charAt(nextCharOffset))) {
      return Result.Continue;
    }

    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    if (file.findElementAt(prevCharOffset) == file.findElementAt(nextCharOffset)) {
      return Result.Continue;
    }

    final int line = document.getLineNumber(caretOffset);
    final int start = document.getLineStartOffset(line);
    final CodeDocumentationUtil.CommentContext commentContext =
      CodeDocumentationUtil.tryParseCommentContext(file, text, caretOffset, start);

    // special case: enter inside "()" or "{}"
    String indentInsideJavadoc = isInComment(caretOffset, file) && commentContext.docAsterisk
                                 ? CodeDocumentationUtil.getIndentInsideJavadoc(document, caretOffset)
                                 : null;

    originalHandler.execute(editor, editor.getCaretModel().getCurrentCaret(), dataContext);

    Project project = editor.getProject();
    if (indentInsideJavadoc != null &&
        project != null &&
        CodeStyleManager.getInstance(project).getDocCommentSettings(file).isLeadingAsteriskEnabled()) {
      document.insertString(editor.getCaretModel().getOffset(), "*" + indentInsideJavadoc);
    }

    PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
    try {
      CodeStyleManager.getInstance(file.getProject()).adjustLineIndent(file, editor.getCaretModel().getOffset());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return indentInsideJavadoc == null ? Result.Continue : Result.DefaultForceIndent;
  }

  private static boolean isInComment(int offset, PsiFile file) {
    return PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiComment.class)!=null;
  }

  private static boolean isValidOffset(int offset, CharSequence text) {
    return offset >= 0 && offset < text.length();
  }

  protected boolean isBracePair(char c1, char c2) {
    return (c1 == '(' && c2 == ')') || (c1 == '{' && c2 == '}');
  }
}

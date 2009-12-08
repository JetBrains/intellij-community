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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 20, 2002
 * Time: 6:21:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class JoinLinesHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.JoinLinesHandler");
  private final EditorActionHandler myOriginalHandler;

  public JoinLinesHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(final Editor editor, final DataContext dataContext) {
    if (!(editor.getDocument() instanceof DocumentEx)) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }
    final DocumentEx doc = (DocumentEx)editor.getDocument();
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getContentComponent()));

    LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();

    final PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = docManager.getPsiFile(doc);

    if (psiFile == null) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }

    int startLine = caretPosition.line;
    int endLine = startLine + 1;
    if (editor.getSelectionModel().hasSelection()) {
      startLine = doc.getLineNumber(editor.getSelectionModel().getSelectionStart());
      endLine = doc.getLineNumber(editor.getSelectionModel().getSelectionEnd());
      if (doc.getLineStartOffset(endLine) == editor.getSelectionModel().getSelectionEnd()) endLine--;
    }

    int caretRestoreOffset = -1;
    for (int i = startLine; i < endLine; i++) {
      if (i >= doc.getLineCount() - 1) break;
      int lineEndOffset = doc.getLineEndOffset(startLine);

      docManager.commitDocument(doc);
      CharSequence text = doc.getCharsSequence();
      int firstNonSpaceOffsetInNextLine = doc.getLineStartOffset(startLine + 1);
      while (firstNonSpaceOffsetInNextLine < text.length() - 1 && (text.charAt(firstNonSpaceOffsetInNextLine) == ' ' || text.charAt(firstNonSpaceOffsetInNextLine) == '\t')) {
        firstNonSpaceOffsetInNextLine++;
      }
      PsiElement elementAtNextLineStart = psiFile.findElementAt(firstNonSpaceOffsetInNextLine);
      boolean isNextLineStartsWithComment = isCommentElement(elementAtNextLineStart);

      int lastNonSpaceOffsetInStartLine = lineEndOffset;
      while (lastNonSpaceOffsetInStartLine > 0 &&
             (text.charAt(lastNonSpaceOffsetInStartLine - 1) == ' ' || text.charAt(lastNonSpaceOffsetInStartLine - 1) == '\t')) {
        lastNonSpaceOffsetInStartLine--;
      }
      int elemOffset = lastNonSpaceOffsetInStartLine > doc.getLineStartOffset(startLine) ? lastNonSpaceOffsetInStartLine - 1 : -1;
      PsiElement elementAtStartLineEnd = elemOffset == -1 ? null : psiFile.findElementAt(elemOffset);
      boolean isStartLineEndsWithComment = isCommentElement(elementAtStartLineEnd);

      if (lastNonSpaceOffsetInStartLine == doc.getLineStartOffset(startLine)) {
        doc.deleteString(doc.getLineStartOffset(startLine), firstNonSpaceOffsetInNextLine);

        int indent = -1;
        try {
          docManager.commitDocument(doc);
          indent = CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, startLine == 0 ? 0 : doc.getLineStartOffset(startLine));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }

        if (caretRestoreOffset == -1) {
          caretRestoreOffset = indent;
        }

        continue;
      }

      doc.deleteString(lineEndOffset, lineEndOffset + doc.getLineSeparatorLength(startLine));

      text = doc.getCharsSequence();
      int start = lineEndOffset - 1;
      int end = lineEndOffset;
      while (start > 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start--;
      while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;

      // Check if we're joining splitted string literal.
      docManager.commitDocument(doc);


      int rc = -1;
      for(JoinLinesHandlerDelegate delegate: Extensions.getExtensions(JoinLinesHandlerDelegate.EP_NAME)) {
        rc = delegate.tryJoinLines(doc, psiFile, start, end);
        if (rc != -1) break;
      }
      docManager.doPostponedOperationsAndUnblockDocument(doc);

      if (rc != -1) {
        if (caretRestoreOffset == -1) caretRestoreOffset = rc;
        continue;
      }

      if (caretRestoreOffset == -1) caretRestoreOffset = start == lineEndOffset ? start : start + 1;


      if (isStartLineEndsWithComment && isNextLineStartsWithComment) {
        if (text.charAt(end) == '*' && end < text.length() && text.charAt(end + 1) != '/') {
          end++;
          while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
        }
        else if (text.charAt(end) == '/') {
          end += 2;
          while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
        }

        doc.replaceString(start == lineEndOffset ? start : start + 1, end, " ");
        continue;
      }

      while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
      doc.replaceString(start == lineEndOffset ? start : start + 1, end, " ");

      if (start <= doc.getLineStartOffset(startLine)) {
        try {
          docManager.commitDocument(doc);
          CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, doc.getLineStartOffset(startLine));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      int prevLineCount = doc.getLineCount();

      docManager.commitDocument(doc);
      try {
        CodeStyleManager.getInstance(project).reformatText(psiFile, start + 1, end);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }

      if (prevLineCount < doc.getLineCount()) {
        docManager.doPostponedOperationsAndUnblockDocument(doc);
        end = doc.getLineEndOffset(startLine) + doc.getLineSeparatorLength(startLine);
        start = end - doc.getLineSeparatorLength(startLine);
        int addedLinesCount = doc.getLineCount() - prevLineCount - 1;
        while (end < doc.getTextLength() &&
               (text.charAt(end) == ' ' || text.charAt(end) == '\t' || text.charAt(end) == '\n' && addedLinesCount > 0)) {
          if (text.charAt(end) == '\n') addedLinesCount--;
          end++;
        }
        doc.replaceString(start, end, " ");
      }

      docManager.commitDocument(doc);
    }

    if (editor.getSelectionModel().hasSelection()) {
      editor.getCaretModel().moveToOffset(editor.getSelectionModel().getSelectionEnd());
    }
    else if (caretRestoreOffset != -1) {
      editor.getCaretModel().moveToOffset(caretRestoreOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }

  private static boolean isCommentElement(final PsiElement element) {
    return element != null && PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null;
  }
}

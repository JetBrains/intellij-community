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
import com.intellij.lang.ASTNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN;

public class JoinLinesHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.JoinLinesHandler");
  private final EditorActionHandler myOriginalHandler;

  public JoinLinesHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  private static TextRange findStartAndEnd(CharSequence text, int start, int end, int maxoffset) {
    while (start > 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start--;
    while (end < maxoffset && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
    return new TextRange(start, end);
  }

  @Override
  public void executeWriteAction(final Editor editor, final DataContext dataContext) {
    if (!(editor.getDocument() instanceof DocumentEx)) {
      myOriginalHandler.execute(editor, dataContext);
      return;
    }
    final DocumentEx doc = (DocumentEx)editor.getDocument();
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getContentComponent()));
    if (project == null) {
      return;
    }

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

    final int startReformatOffset = CharArrayUtil.shiftBackward(doc.getCharsSequence(), doc.getLineEndOffset(startLine), " \t");
    CodeEditUtil.setNodeReformatStrategy(new NotNullFunction<ASTNode, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(ASTNode node) {
        return node.getTextRange().getStartOffset() >= startReformatOffset;
      }
    });
    try {
      doJob(editor, doc, project, docManager, psiFile, startLine, endLine);
    }
    finally {
      CodeEditUtil.setNodeReformatStrategy(null);
    }
  }

  private static void doJob(Editor editor,
                            DocumentEx doc,
                            Project project,
                            PsiDocumentManager docManager,
                            PsiFile psiFile,
                            int startLine,
                            int endLine)
  {
    int caretRestoreOffset = -1;
    // joining lines, several times if selection is multiline
    for (int i = startLine; i < endLine; i++) {
      if (i >= doc.getLineCount() - 1) break;
      int lineEndOffset = doc.getLineEndOffset(startLine);

      docManager.doPostponedOperationsAndUnblockDocument(doc);
      docManager.commitDocument(doc);
      CharSequence text = doc.getCharsSequence();
      int firstNonSpaceOffsetInNextLine = doc.getLineStartOffset(startLine + 1);
      while (firstNonSpaceOffsetInNextLine < text.length() - 1
             && (text.charAt(firstNonSpaceOffsetInNextLine) == ' ' || text.charAt(firstNonSpaceOffsetInNextLine) == '\t'))
      {
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

      int rc = -1;
      int start;
      int end;
      TextRange limits = findStartAndEnd(text, lastNonSpaceOffsetInStartLine, firstNonSpaceOffsetInNextLine, doc.getTextLength());
      start = limits.getStartOffset(); end = limits.getEndOffset();
      // run raw joiners
      for(JoinLinesHandlerDelegate delegate: Extensions.getExtensions(JoinLinesHandlerDelegate.EP_NAME)) {
        if (delegate instanceof JoinRawLinesHandlerDelegate) {
          rc = ((JoinRawLinesHandlerDelegate)delegate).tryJoinRawLines(doc, psiFile, start, end);
          if (rc != CANNOT_JOIN) {
            caretRestoreOffset = rc;
            break;
          }
        }
      }
      if (rc == CANNOT_JOIN) { // remove indents and newline, run non-raw joiners
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

          if (caretRestoreOffset == CANNOT_JOIN) {
            caretRestoreOffset = indent;
          }

          continue;
        }

        doc.deleteString(lineEndOffset, lineEndOffset + doc.getLineSeparatorLength(startLine));

        text = doc.getCharsSequence();
        limits = findStartAndEnd(text, lineEndOffset - 1, lineEndOffset, doc.getTextLength());
        start = limits.getStartOffset(); end = limits.getEndOffset();

        // Check if we're joining splitted string literal.
        docManager.commitDocument(doc);

        for(JoinLinesHandlerDelegate delegate: Extensions.getExtensions(JoinLinesHandlerDelegate.EP_NAME)) {
          rc = delegate.tryJoinLines(doc, psiFile, start, end);
          if (rc != CANNOT_JOIN) break;
        }
      }
      docManager.doPostponedOperationsAndUnblockDocument(doc);

      if (rc != CANNOT_JOIN) {
        if (caretRestoreOffset == CANNOT_JOIN) caretRestoreOffset = rc;
        continue;
      }


      if (caretRestoreOffset == CANNOT_JOIN) caretRestoreOffset = start == lineEndOffset ? start : start + 1;


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
    docManager.commitDocument(doc); // cheap on an already-committed doc

    if (editor.getSelectionModel().hasSelection()) {
      editor.getCaretModel().moveToOffset(editor.getSelectionModel().getSelectionEnd());
    }
    else if (caretRestoreOffset != CANNOT_JOIN) {
      editor.getCaretModel().moveToOffset(caretRestoreOffset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().removeSelection();
    }
  }

  private static boolean isCommentElement(final PsiElement element) {
    return element != null && PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null;
  }
}

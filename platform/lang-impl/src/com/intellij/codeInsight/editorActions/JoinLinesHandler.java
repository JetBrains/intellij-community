/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN;

public class JoinLinesHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.JoinLinesHandler");
  private final EditorActionHandler myOriginalHandler;

  public JoinLinesHandler(EditorActionHandler originalHandler) {
    super(true);
    myOriginalHandler = originalHandler;
  }

  @NotNull
  private static TextRange findStartAndEnd(@NotNull CharSequence text, int start, int end, int maxoffset) {
    while (start > 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start--;
    while (end < maxoffset && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
    return new TextRange(start, end);
  }

  @Override
  public void executeWriteAction(@NotNull final Editor editor, @Nullable Caret caret, final DataContext dataContext) {
    assert caret != null;
    if (!(editor.getDocument() instanceof DocumentEx)) {
      myOriginalHandler.execute(editor, caret, dataContext);
      return;
    }
    final DocumentEx doc = (DocumentEx)editor.getDocument();
    final Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getContentComponent()));
    if (project == null) return;

    final PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = docManager.getPsiFile(doc);

    if (psiFile == null) {
      myOriginalHandler.execute(editor, caret, dataContext);
      return;
    }

    LogicalPosition caretPosition = caret.getLogicalPosition();
    int startLine = caretPosition.line;
    int endLine = startLine + 1;
    if (caret.hasSelection()) {
      startLine = doc.getLineNumber(caret.getSelectionStart());
      endLine = doc.getLineNumber(caret.getSelectionEnd());
      if (doc.getLineStartOffset(endLine) == caret.getSelectionEnd()) endLine--;
    }

    final int startReformatOffset = CharArrayUtil.shiftBackward(doc.getCharsSequence(), doc.getLineEndOffset(startLine), " \t");
    CodeEditUtil.setNodeReformatStrategy(new NotNullFunction<ASTNode, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(@NotNull ASTNode node) {
        return node.getTextRange().getStartOffset() >= startReformatOffset;
      }
    });
    try {
      doJob(editor, doc, caret, project, docManager, psiFile, startLine, endLine);
    }
    finally {
      CodeEditUtil.setNodeReformatStrategy(null);
    }
  }

  private static void doJob(@NotNull Editor editor,
                            @NotNull DocumentEx doc,
                            @NotNull Caret caret, 
                            @NotNull Project project,
                            @NotNull PsiDocumentManager docManager,
                            @NotNull PsiFile psiFile,
                            int startLine,
                            int endLine) {
    int caretRestoreOffset = -1;
    // joining lines, several times if selection is multiline
    for (int i = startLine; i < endLine; i++) {
      if (i >= doc.getLineCount() - 1) break;

      docManager.doPostponedOperationsAndUnblockDocument(doc);
      docManager.commitDocument(doc);
      CharSequence text = doc.getCharsSequence();
      JoinLinesOffsets offsets = calcJoinLinesOffsets(psiFile, doc, startLine);

      if (offsets.isStartLineEndsWithComment && !offsets.isNextLineStartsWithComment) {
        tryConvertEndOfLineComment(doc, offsets.elementAtStartLineEnd);
        offsets = calcJoinLinesOffsets(psiFile, doc, startLine);
      }

      int rc = -1;
      int start;
      int end;
      TextRange limits = findStartAndEnd(text, offsets.lastNonSpaceOffsetInStartLine, offsets.firstNonSpaceOffsetInNextLine, doc.getTextLength());
      start = limits.getStartOffset(); end = limits.getEndOffset();
      // run raw joiners
      for (JoinLinesHandlerDelegate delegate: Extensions.getExtensions(JoinLinesHandlerDelegate.EP_NAME)) {
        if (delegate instanceof JoinRawLinesHandlerDelegate) {
          rc = ((JoinRawLinesHandlerDelegate)delegate).tryJoinRawLines(doc, psiFile, start, end);
          if (rc != CANNOT_JOIN) {
            caretRestoreOffset = rc;
            break;
          }
        }
      }
      if (rc == CANNOT_JOIN) { // remove indents and newline, run non-raw joiners
        if (offsets.lastNonSpaceOffsetInStartLine == doc.getLineStartOffset(startLine)) {
          doc.deleteString(doc.getLineStartOffset(startLine), offsets.firstNonSpaceOffsetInNextLine);

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

        doc.deleteString(offsets.lineEndOffset, offsets.lineEndOffset + doc.getLineSeparatorLength(startLine));

        text = doc.getCharsSequence();
        limits = findStartAndEnd(text, offsets.lineEndOffset - 1, offsets.lineEndOffset, doc.getTextLength());
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


      if (caretRestoreOffset == CANNOT_JOIN) caretRestoreOffset = start == offsets.lineEndOffset ? start : start + 1;


      if (offsets.isStartLineEndsWithComment && offsets.isNextLineStartsWithComment) {
        if (text.charAt(end) == '*' && end < text.length() && text.charAt(end + 1) != '/') {
          end++;
          while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
        }
        else if (text.charAt(end) == '/') {
          end += 2;
          while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
        }

        doc.replaceString(start == offsets.lineEndOffset ? start : start + 1, end, " ");
        continue;
      }

      while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
      doc.replaceString(start == offsets.lineEndOffset ? start : start + 1, end, " ");

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
        CodeStyleManager.getInstance(project).reformatRange(psiFile, start + 1, end, true);
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

    if (caret.hasSelection()) {
      caret.moveToOffset(caret.getSelectionEnd());
    }
    else if (caretRestoreOffset != CANNOT_JOIN) {
      caret.moveToOffset(caretRestoreOffset);
      if (caret == editor.getCaretModel().getPrimaryCaret()) { // performance
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
      caret.removeSelection();
    }
  }

  private static class JoinLinesOffsets {
    int lineEndOffset;
    int lastNonSpaceOffsetInStartLine;
    int firstNonSpaceOffsetInNextLine;
    boolean isStartLineEndsWithComment;
    boolean isNextLineStartsWithComment;
    PsiElement elementAtStartLineEnd;
  }

  private static JoinLinesOffsets calcJoinLinesOffsets(PsiFile psiFile, Document doc, int startLine) {
    JoinLinesOffsets offsets = new JoinLinesOffsets();
    CharSequence text = doc.getCharsSequence();
    offsets.lineEndOffset = doc.getLineEndOffset(startLine);
    offsets.firstNonSpaceOffsetInNextLine = doc.getLineStartOffset(startLine + 1);
    while (offsets.firstNonSpaceOffsetInNextLine < text.length() - 1
           && (text.charAt(offsets.firstNonSpaceOffsetInNextLine) == ' ' || text.charAt(offsets.firstNonSpaceOffsetInNextLine) == '\t'))
    {
      offsets.firstNonSpaceOffsetInNextLine++;
    }
    PsiElement elementAtNextLineStart = psiFile.findElementAt(offsets.firstNonSpaceOffsetInNextLine);
    offsets.isNextLineStartsWithComment = isCommentElement(elementAtNextLineStart);

    offsets.lastNonSpaceOffsetInStartLine = offsets.lineEndOffset;
    while (offsets.lastNonSpaceOffsetInStartLine > 0 &&
           (text.charAt(offsets.lastNonSpaceOffsetInStartLine - 1) == ' ' || text.charAt(offsets.lastNonSpaceOffsetInStartLine - 1) == '\t')) {
      offsets.lastNonSpaceOffsetInStartLine--;
    }
    int elemOffset = offsets.lastNonSpaceOffsetInStartLine > doc.getLineStartOffset(startLine) ? offsets.lastNonSpaceOffsetInStartLine - 1 : -1;
    offsets.elementAtStartLineEnd = elemOffset == -1 ? null : psiFile.findElementAt(elemOffset);
    offsets.isStartLineEndsWithComment = isCommentElement(offsets.elementAtStartLineEnd);
    return offsets;

  }

  private static void tryConvertEndOfLineComment(Document doc, PsiElement commentElement) {
    Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(commentElement.getLanguage());
    if (commenter instanceof CodeDocumentationAwareCommenter) {
      CodeDocumentationAwareCommenter docCommenter = (CodeDocumentationAwareCommenter) commenter;
      String lineCommentPrefix = commenter.getLineCommentPrefix();
      String blockCommentPrefix = commenter.getBlockCommentPrefix();
      String blockCommentSuffix = commenter.getBlockCommentSuffix();
      if (commentElement.getNode().getElementType() == docCommenter.getLineCommentTokenType() &&
        blockCommentPrefix != null && blockCommentSuffix != null && lineCommentPrefix != null) {
        String commentText = StringUtil.trimStart(commentElement.getText(), lineCommentPrefix);
        try {
          Project project = commentElement.getProject();
          PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(project);
          PsiComment newComment = parserFacade.createBlockCommentFromText(commentElement.getLanguage(), commentText);
          commentElement.replace(newComment);
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc);
        }
        catch (IncorrectOperationException e) {
          LOG.info("Failed to replace line comment with block comment", e);
        }
      }
    }
  }

  private static boolean isCommentElement(@Nullable final PsiElement element) {
    return element != null && PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null;
  }
}

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
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.impl.source.jsp.jspJava.JspTemplateStatement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class StatementMover extends LineMover {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.StatementMover");

  private PsiElement statementToSurroundWithCodeBlock;

  @Override
  public void beforeMove(@NotNull final Editor editor, @NotNull final MoveInfo info, final boolean down) {
    super.beforeMove(editor, info, down);
    if (statementToSurroundWithCodeBlock != null) {
      surroundWithCodeBlock(info, down);
    }
  }

  private void surroundWithCodeBlock(@NotNull final MoveInfo info, final boolean down) {
    try {
      final Document document = PsiDocumentManager.getInstance(statementToSurroundWithCodeBlock.getProject()).getDocument(statementToSurroundWithCodeBlock.getContainingFile());
      int startOffset = document.getLineStartOffset(info.toMove.startLine);
      int endOffset = getLineStartSafeOffset(document, info.toMove.endLine);
      if (document.getText().charAt(endOffset-1) == '\n') endOffset--;
      final RangeMarker lineRangeMarker = document.createRangeMarker(startOffset, endOffset);

      final PsiElementFactory factory = JavaPsiFacade.getInstance(statementToSurroundWithCodeBlock.getProject()).getElementFactory();
      PsiCodeBlock codeBlock = factory.createCodeBlock();
      codeBlock.add(statementToSurroundWithCodeBlock);
      final PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", statementToSurroundWithCodeBlock);
      blockStatement.getCodeBlock().replace(codeBlock);
      PsiBlockStatement newStatement = (PsiBlockStatement)statementToSurroundWithCodeBlock.replace(blockStatement);
      newStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newStatement);
      info.toMove = new LineRange(document.getLineNumber(lineRangeMarker.getStartOffset()), document.getLineNumber(lineRangeMarker.getEndOffset())+1);
      PsiCodeBlock newCodeBlock = newStatement.getCodeBlock();
      if (down) {
        PsiElement blockChild = firstNonWhiteElement(newCodeBlock.getFirstBodyElement(), true);
        if (blockChild == null) blockChild = newCodeBlock.getRBrace();
        info.toMove2 = new LineRange(info.toMove2.startLine, //document.getLineNumber(newCodeBlock.getParent().getTextRange().getStartOffset()),
                                document.getLineNumber(blockChild.getTextRange().getStartOffset()));
      }
      else {
        int start = document.getLineNumber(newCodeBlock.getRBrace().getTextRange().getStartOffset());
        int end = info.toMove.startLine;
        if (start > end) end = start;
        info.toMove2  = new LineRange(start, end);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean checkAvailable(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down) {
    //if (!(file instanceof PsiJavaFile)) return false;
    final boolean available = super.checkAvailable(editor, file, info, down);
    if (!available) return false;
    LineRange range = info.toMove;

    range = expandLineRangeToCoverPsiElements(range, editor, file);
    if (range == null) return false;
    info.toMove = range;
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0));
    final PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) return false;
    range.firstElement = statements[0];
    range.lastElement = statements[statements.length-1];

    if (!checkMovingInsideOutside(file, editor, info, down)) {
      info.toMove2 = null;
      return true;
    }
    return true;
  }

  private static int getDestLineForAnon(Editor editor, LineRange range, boolean down) {
    int destLine = down ? range.endLine+1 : range.startLine - 1;
    if (!(range.firstElement instanceof PsiStatement)) {
      return destLine;
    }
    PsiElement sibling =
      StatementUpDownMover.firstNonWhiteElement(down ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling(), down);
    final PsiClass aClass = findChildOfType(sibling, PsiClass.class, PsiStatement.class);
    if (aClass != null && PsiTreeUtil.getParentOfType(aClass, PsiStatement.class) == sibling) {
      destLine =
        editor.getDocument().getLineNumber(down ? sibling.getTextRange().getEndOffset() + 1 : sibling.getTextRange().getStartOffset());
    }
    return destLine;
  }

  @Nullable
  private static <T extends PsiElement> T findChildOfType(@Nullable final PsiElement element,
                                                          @NotNull final Class<T> aClass,
                                                          @Nullable final Class<? extends PsiElement> stopAt) {
    final PsiElementProcessor.FindElement<PsiElement> processor = new PsiElementProcessor.FindElement<PsiElement>() {
      @Override
      public boolean execute(@NotNull PsiElement each) {
        if (each == element) return true; // strict
        if (aClass.isInstance(each)) {
          return setFound(each);
        }
        return stopAt == null || !stopAt.isInstance(each);
      }
    };

    PsiTreeUtil.processElements(element, processor);
    //noinspection unchecked
    return (T)processor.getFoundElement();
  }

  private boolean calcInsertOffset(@NotNull PsiFile file, @NotNull Editor editor, @NotNull LineRange range, @NotNull final MoveInfo info, final boolean down) {
    int destLine = getDestLineForAnon(editor, range, down);

    int startLine = down ? range.endLine : range.startLine - 1;
    if (destLine < 0 || startLine < 0) return false;
    while (true) {
      final int offset = editor.logicalPositionToOffset(new LogicalPosition(destLine, 0));
      PsiElement element = firstNonWhiteElement(offset, file, true);

      while (element != null && !(element instanceof PsiFile)) {
        TextRange elementTextRange = element.getTextRange();
        if (elementTextRange.isEmpty() || !elementTextRange.grown(-1).shiftRight(1).contains(offset)) {
          PsiElement elementToSurround = null;
          boolean found = false;
          if ((element instanceof PsiStatement || element instanceof PsiComment)
              && statementCanBePlacedAlong(element)) {
            found = true;
            if (!(element.getParent() instanceof PsiCodeBlock)) {
              elementToSurround = element;
            }
          }
          else if (element instanceof PsiJavaToken
                   && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE
                   && element.getParent() instanceof PsiCodeBlock) {
            // before code block closing brace
            found = true;
          }
          if (found) {
            statementToSurroundWithCodeBlock = elementToSurround;
            info.toMove = range;
            int endLine = destLine;
            if (startLine > endLine) {
              int tmp = endLine;
              endLine = startLine;
              startLine = tmp;
            }

            info.toMove2 = down ? new LineRange(startLine, endLine) : new LineRange(startLine, endLine+1);
            return true;
          }
        }
        element = element.getParent();
      }
      destLine += down ? 1 : -1;
      if (destLine == 0 || destLine >= editor.getDocument().getLineCount()) {
        return false;
      }
    }
  }

  private static boolean statementCanBePlacedAlong(final PsiElement element) {
    if (element instanceof JspTemplateStatement) {
      PsiElement neighbour = element.getPrevSibling();
      // we can place statement inside scriptlet only
      return neighbour != null && !(neighbour instanceof JspTemplateStatement);
    }
    if (element instanceof PsiBlockStatement) return false;
    final PsiElement parent = element.getParent();
    if (parent instanceof JspClassLevelDeclarationStatement) return false;
    if (parent instanceof PsiCodeBlock) return true;
    if (parent instanceof PsiIfStatement &&
        (element == ((PsiIfStatement)parent).getThenBranch() || element == ((PsiIfStatement)parent).getElseBranch())) {
      return true;
    }
    if (parent instanceof PsiWhileStatement && element == ((PsiWhileStatement)parent).getBody()) {
      return true;
    }
    if (parent instanceof PsiDoWhileStatement && element == ((PsiDoWhileStatement)parent).getBody()) {
      return true;
    }
    // know nothing about that
    return false;
  }

  private boolean checkMovingInsideOutside(PsiFile file, final Editor editor, @NotNull final MoveInfo info, final boolean down) {
    final int offset = editor.getCaretModel().getOffset();

    PsiElement elementAtOffset = file.getViewProvider().findElementAt(offset, StdLanguages.JAVA);
    if (elementAtOffset == null) return false;

    PsiElement guard = elementAtOffset;
    do {
      guard = PsiTreeUtil.getParentOfType(guard, PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiComment.class);
    }
    while (guard instanceof PsiAnonymousClass);

    PsiElement brace = itIsTheClosingCurlyBraceWeAreMoving(file, editor);
    if (brace != null) {
      int line = editor.getDocument().getLineNumber(offset);
      final LineRange toMove = new LineRange(line, line + 1);
      toMove.firstElement = toMove.lastElement = brace;
      info.toMove = toMove;
    }

    // cannot move in/outside method/class/initializer/comment
    if (!calcInsertOffset(file, editor, info.toMove, info, down)) return false;
    int insertOffset = down ? getLineStartSafeOffset(editor.getDocument(), info.toMove2.endLine) : editor.getDocument().getLineStartOffset(info.toMove2.startLine);
    PsiElement elementAtInsertOffset = file.getViewProvider().findElementAt(insertOffset, StdLanguages.JAVA);
    PsiElement newGuard = elementAtInsertOffset;
    do {
      newGuard = PsiTreeUtil.getParentOfType(newGuard, PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiComment.class);
    }
    while (newGuard instanceof PsiAnonymousClass);

    if (brace != null && PsiTreeUtil.getParentOfType(brace, PsiCodeBlock.class, false) !=
                         PsiTreeUtil.getParentOfType(elementAtInsertOffset, PsiCodeBlock.class, false)) {
      info.indentSource = true;
    }
    if (newGuard == guard && isInside(insertOffset, newGuard) == isInside(offset, guard)) return true;

    // moving in/out nested class is OK
    if (guard instanceof PsiClass && guard.getParent() instanceof PsiClass) return true;
    if (newGuard instanceof PsiClass && newGuard.getParent() instanceof PsiClass) return true;

    return false;
  }

  private static boolean isInside(final int offset, final PsiElement guard) {
    if (guard == null) return false;
    TextRange inside = guard instanceof PsiMethod
                       ? ((PsiMethod)guard).getBody().getTextRange()
                       : guard instanceof PsiClassInitializer
                         ? ((PsiClassInitializer)guard).getBody().getTextRange()
                         : guard instanceof PsiClass ? new TextRange(((PsiClass)guard).getLBrace().getTextOffset(),
                                                                     ((PsiClass)guard).getRBrace().getTextOffset()) : guard.getTextRange();
    return inside != null && inside.contains(offset);
  }

  private static LineRange expandLineRangeToCoverPsiElements(final LineRange range, Editor editor, final PsiFile file) {
    Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, range);
    if (psiRange == null) return null;
    final PsiElement parent = PsiTreeUtil.findCommonParent(psiRange.getFirst(), psiRange.getSecond());
    Pair<PsiElement, PsiElement> elementRange = getElementRange(parent, psiRange.getFirst(), psiRange.getSecond());
    if (elementRange == null) return null;
    int endOffset = elementRange.getSecond().getTextRange().getEndOffset();
    Document document = editor.getDocument();
    if (endOffset > document.getTextLength()) {
      LOG.assertTrue(!PsiDocumentManager.getInstance(file.getProject()).isUncommited(document));
      LOG.assertTrue(PsiDocumentManagerImpl.checkConsistency(file, document));
    }
    int endLine;
    if (endOffset == document.getTextLength()) {
      endLine = document.getLineCount();
    }
    else {
      endLine = editor.offsetToLogicalPosition(endOffset).line+1;
      endLine = Math.min(endLine, document.getLineCount());
    }
    int startLine = Math.min(range.startLine, editor.offsetToLogicalPosition(elementRange.getFirst().getTextOffset()).line);
    endLine = Math.max(endLine, range.endLine);
    return new LineRange(startLine, endLine);
  }

  private static PsiElement itIsTheClosingCurlyBraceWeAreMoving(final PsiFile file, final Editor editor) {
    LineRange range = getLineRangeFromSelection(editor);
    if (range.endLine - range.startLine != 1) return null;
    int offset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    int line = document.getLineNumber(offset);
    int lineStartOffset = document.getLineStartOffset(line);
    String lineText = document.getText().substring(lineStartOffset, document.getLineEndOffset(line));
    if (!lineText.trim().equals("}")) return null;

    return file.findElementAt(lineStartOffset + lineText.indexOf('}'));
  }
}


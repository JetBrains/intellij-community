// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.impl.source.jsp.jspJava.JspTemplateStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

class StatementMover extends LineMover {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.StatementMover");
  private static final Key<PsiElement> STATEMENT_TO_SURROUND_WITH_CODE_BLOCK_KEY = Key.create("STATEMENT_TO_SURROUND_WITH_CODE_BLOCK_KEY");

  @Override
  public void beforeMove(@NotNull Editor editor, @NotNull MoveInfo info, boolean down) {
    super.beforeMove(editor, info, down);

    PsiElement statement = STATEMENT_TO_SURROUND_WITH_CODE_BLOCK_KEY.get(info);
    if (statement != null) {
      surroundWithCodeBlock(info, down, statement);
    }
  }

  private static void surroundWithCodeBlock(MoveInfo info, boolean down, PsiElement statement) {
    try {
      Document document = PsiDocumentManager.getInstance(statement.getProject()).getDocument(statement.getContainingFile());
      assert document != null : statement.getContainingFile();
      int startOffset = document.getLineStartOffset(info.toMove.startLine);
      int endOffset = getLineStartSafeOffset(document, info.toMove.endLine);
      if (document.getText().charAt(endOffset - 1) == '\n') endOffset--;
      RangeMarker lineRangeMarker = document.createRangeMarker(startOffset, endOffset);

      PsiElementFactory factory = JavaPsiFacade.getInstance(statement.getProject()).getElementFactory();
      PsiCodeBlock codeBlock = factory.createCodeBlock();
      codeBlock.add(statement);
      PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", statement);
      blockStatement.getCodeBlock().replace(codeBlock);
      PsiBlockStatement newStatement = (PsiBlockStatement)statement.replace(blockStatement);
      newStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newStatement);
      info.toMove = new LineRange(document.getLineNumber(lineRangeMarker.getStartOffset()), document.getLineNumber(lineRangeMarker.getEndOffset())+1);
      PsiCodeBlock newCodeBlock = newStatement.getCodeBlock();
      if (down) {
        PsiElement blockChild = firstNonWhiteElement(newCodeBlock.getFirstBodyElement(), true);
        if (blockChild == null) blockChild = newCodeBlock.getRBrace();
        assert blockChild != null : newCodeBlock;
        info.toMove2 = new LineRange(info.toMove2.startLine, document.getLineNumber(blockChild.getTextRange().getStartOffset()));
      }
      else {
        PsiJavaToken brace = newCodeBlock.getRBrace();
        assert brace != null : newCodeBlock;
        int start = document.getLineNumber(brace.getTextRange().getStartOffset());
        int end = info.toMove.startLine;
        if (start > end) end = start;
        info.toMove2 = new LineRange(start, end);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    boolean available = super.checkAvailable(editor, file, info, down);
    if (!available) return false;

    LineRange range = expandLineRangeToCoverPsiElements(info.toMove, editor, file);
    if (range == null) return false;

    info.toMove = range;
    int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0));
    PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) return false;

    range.firstElement = statements[0];
    range.lastElement = statements[statements.length - 1];

    if (!checkMovingInsideOutside(file, editor, info, down)) {
      return info.prohibitMove();
    }

    return true;
  }

  private static int getDestLineForAnonymous(Editor editor, LineRange range, boolean down) {
    int destLine = down ? range.endLine + 1 : range.startLine - 1;
    if (!(range.firstElement instanceof PsiStatement)) {
      return destLine;
    }

    PsiElement sibling = firstNonWhiteElement(down ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling(), down);
    if (sibling != null) {
      PsiClass aClass = PsiTreeUtil.findChildOfType(sibling, PsiClass.class, true, PsiStatement.class);
      if (aClass != null && PsiTreeUtil.getParentOfType(aClass, PsiStatement.class) == sibling) {
        destLine = editor.getDocument().getLineNumber(down ? sibling.getTextRange().getEndOffset() + 1 : sibling.getTextRange().getStartOffset());
      }
    }

    return destLine;
  }

  private static boolean calcInsertOffset(PsiFile file, Editor editor, LineRange range, MoveInfo info, boolean down) {
    int destLine = getDestLineForAnonymous(editor, range, down);
    int startLine = down ? range.endLine : range.startLine - 1;
    if (destLine < 0 || startLine < 0) return false;

    while (true) {
      int offset = editor.logicalPositionToOffset(new LogicalPosition(destLine, 0));
      PsiElement element = firstNonWhiteElement(offset, file, true);

      while (element != null && !(element instanceof PsiFile)) {
        TextRange elementTextRange = element.getTextRange();
        if (elementTextRange.isEmpty() || !elementTextRange.grown(-1).shiftRight(1).contains(offset)) {
          PsiElement elementToSurround = null;
          boolean found = false;
          if ((element instanceof PsiStatement || element instanceof PsiComment) && statementCanBePlacedAlong(element)) {
            found = true;
            if (!(statementsCanBeMovedWithin(element.getParent()))) {
              elementToSurround = element;
            }
          }
          else if (PsiUtil.isJavaToken(element, JavaTokenType.RBRACE) && statementsCanBeMovedWithin(element.getParent())) {
            // before code block closing brace
            found = true;
          }
          if (found) {
            STATEMENT_TO_SURROUND_WITH_CODE_BLOCK_KEY.set(info, elementToSurround);
            info.toMove = range;
            int endLine = destLine;
            if (startLine > endLine) {
              int tmp = endLine;
              endLine = startLine;
              startLine = tmp;
            }

            info.toMove2 = down ? new LineRange(startLine, endLine) : new LineRange(startLine, endLine + 1);
            return true;
          }
        }
        element = element.getParent();
      }

      destLine += down ? 1 : -1;
      if (destLine < 0 || destLine >= editor.getDocument().getLineCount()) {
        return false;
      }
    }
  }

  private static boolean statementCanBePlacedAlong(PsiElement element) {
    if (element instanceof JspTemplateStatement) {
      PsiElement neighbour = element.getPrevSibling();
      // we can place statement inside scriptlet only
      return neighbour != null && !(neighbour instanceof JspTemplateStatement);
    }
    if (element instanceof PsiBlockStatement) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof JspClassLevelDeclarationStatement) return false;
    if (statementsCanBeMovedWithin(parent)) return true;
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

  private static boolean statementsCanBeMovedWithin(PsiElement parent) {
    return parent instanceof PsiCodeBlock || parent instanceof PsiJavaModule;
  }

  private static boolean checkMovingInsideOutside(PsiFile file, Editor editor, @NotNull MoveInfo info, boolean down) {
    int offset = editor.getCaretModel().getOffset();

    PsiElement elementAtOffset = file.getViewProvider().findElementAt(offset, JavaLanguage.INSTANCE);
    if (elementAtOffset == null) return false;

    PsiElement guard = findGuard(elementAtOffset);

    PsiElement brace = itIsTheClosingCurlyBraceWeAreMoving(file, editor);
    if (brace != null) {
      int line = editor.getDocument().getLineNumber(offset);
      LineRange toMove = new LineRange(line, line + 1);
      toMove.firstElement = toMove.lastElement = brace;
      info.toMove = toMove;
    }

    // cannot move in/outside method/class/initializer/comment
    if (!calcInsertOffset(file, editor, info.toMove, info, down)) return false;

    int insertOffset = down ? getLineStartSafeOffset(editor.getDocument(), info.toMove2.endLine)
                            : editor.getDocument().getLineStartOffset(info.toMove2.startLine);
    PsiElement elementAtInsertOffset = file.getViewProvider().findElementAt(insertOffset, JavaLanguage.INSTANCE);
    PsiElement newGuard = findGuard(elementAtInsertOffset);

    if (brace != null &&
        PsiTreeUtil.getParentOfType(brace, PsiCodeBlock.class, false) != PsiTreeUtil.getParentOfType(elementAtInsertOffset, PsiCodeBlock.class, false)) {
      info.indentSource = true;
    }

    if (newGuard == guard && isInside(insertOffset, newGuard) == isInside(offset, guard)) return true;

    // moving in/out nested class is OK
    if (guard instanceof PsiClass && guard.getParent() instanceof PsiClass) return true;
    if (newGuard instanceof PsiClass && newGuard.getParent() instanceof PsiClass) return true;

    return false;
  }

  private static PsiElement findGuard(PsiElement element) {
    PsiElement guard = element;
    do {
      guard = PsiTreeUtil.getParentOfType(guard, PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiComment.class);
    }
    while (guard instanceof PsiAnonymousClass);
    return guard;
  }

  private static boolean isInside(int offset, PsiElement guard) {
    if (guard == null) return false;

    TextRange inside;
    if (guard instanceof PsiMethod) {
      PsiCodeBlock body = ((PsiMethod)guard).getBody();
      inside = body != null ? body.getTextRange() : null;
    }
    else if (guard instanceof PsiClassInitializer) {
      inside = ((PsiClassInitializer)guard).getBody().getTextRange();
    }
    else if (guard instanceof PsiClass) {
      PsiElement left = ((PsiClass)guard).getLBrace(), right = ((PsiClass)guard).getRBrace();
      inside = left != null && right != null ? new TextRange(left.getTextOffset(), right.getTextOffset()) : null;
    }
    else {
      inside = guard.getTextRange();
    }
    return inside != null && inside.contains(offset);
  }

  private static LineRange expandLineRangeToCoverPsiElements(LineRange range, Editor editor, PsiFile file) {
    Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, range);
    if (psiRange == null) return null;
    PsiElement parent = PsiTreeUtil.findCommonParent(psiRange.getFirst(), psiRange.getSecond());
    Pair<PsiElement, PsiElement> elementRange = getElementRange(parent, psiRange.getFirst(), psiRange.getSecond());
    if (elementRange == null) return null;
    int endOffset = elementRange.getSecond().getTextRange().getEndOffset();
    Document document = editor.getDocument();
    if (endOffset > document.getTextLength()) {
      LOG.assertTrue(!PsiDocumentManager.getInstance(file.getProject()).isUncommited(document));
      LOG.assertTrue(PsiDocumentManagerBase.checkConsistency(file, document));
    }
    int endLine;
    if (endOffset == document.getTextLength()) {
      endLine = document.getLineCount();
    }
    else {
      endLine = editor.offsetToLogicalPosition(endOffset).line + 1;
      endLine = Math.min(endLine, document.getLineCount());
    }
    int startLine = Math.min(range.startLine, editor.offsetToLogicalPosition(elementRange.getFirst().getTextOffset()).line);
    endLine = Math.max(endLine, range.endLine);
    return new LineRange(startLine, endLine);
  }

  private static PsiElement itIsTheClosingCurlyBraceWeAreMoving(PsiFile file, Editor editor) {
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
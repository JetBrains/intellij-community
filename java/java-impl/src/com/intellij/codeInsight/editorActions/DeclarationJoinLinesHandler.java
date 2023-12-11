// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class DeclarationJoinLinesHandler implements JoinLinesHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(DeclarationJoinLinesHandler.class);

  @Override
  public int tryJoinLines(@NotNull final Document document, @NotNull final PsiFile file, final int start, final int end) {
    PsiElement elementAtStartLineEnd = file.findElementAt(start);
    PsiElement elementAtNextLineStart = file.findElementAt(end);
    if (elementAtStartLineEnd == null || elementAtNextLineStart == null) return -1;

    // first line.
    if (!PsiUtil.isJavaToken(elementAtStartLineEnd, JavaTokenType.SEMICOLON)) return -1;
    PsiLocalVariable var = ObjectUtils.tryCast(elementAtStartLineEnd.getParent(), PsiLocalVariable.class);
    if (var == null) return -1;

    if (!(var.getParent() instanceof PsiDeclarationStatement decl)) return -1;
    if (decl.getDeclaredElements().length > 1) return -1;

    //second line.
    if (!PsiUtil.isJavaToken(elementAtNextLineStart, JavaTokenType.IDENTIFIER)) return -1;
    if (!(elementAtNextLineStart.getParent() instanceof PsiReferenceExpression ref)) return -1;
    PsiElement refResolved = ref.resolve();

    PsiManager psiManager = ref.getManager();
    if (!psiManager.areElementsEquivalent(refResolved, var)) return -1;
    if (!(ref.getParent() instanceof PsiAssignmentExpression assignment)) return -1;
    if (!(assignment.getParent() instanceof PsiExpressionStatement statement)) return -1;

    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return -1;

    if (ReferencesSearch.search(var, new LocalSearchScope(rExpression), false).findFirst() != null) {
      return -1;
    }

    final PsiExpression initializerExpression = getInitializerExpression(var, assignment);
    if (initializerExpression == null) return -1;

    int startOffset = decl.getTextRange().getStartOffset();
    try {
      PsiLocalVariable variable = copyVarWithInitializer(var, initializerExpression);
      if (variable == null) return -1;
      PsiDeclarationStatement newDecl = (PsiDeclarationStatement)variable.getParent();
      final int offsetBeforeEQ = Objects.requireNonNull(variable.getNameIdentifier()).getTextRange().getEndOffset();
      final int offsetAfterEQ = Objects.requireNonNull(variable.getInitializer()).getTextRange().getStartOffset() + 1;
      newDecl = (PsiDeclarationStatement)CodeStyleManager.getInstance(psiManager).reformatRange(newDecl, offsetBeforeEQ, offsetAfterEQ);

      PsiElement child = statement.getLastChild();
      while (child instanceof PsiComment || child instanceof PsiWhiteSpace) {
        child = child.getPrevSibling();
      }
      if (child != null && child.getNextSibling() != null) {
        newDecl.addRangeBefore(child.getNextSibling(), statement.getLastChild(), null);
      }

      PsiElement prev = statement.getPrevSibling();
      if (prev instanceof PsiWhiteSpace) {
        prev.delete();
      }
      statement.delete();
      decl.replace(newDecl);
      return startOffset + newDecl.getTextRange().getEndOffset() - newDecl.getTextRange().getStartOffset();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return -1;
    }
  }

  /**
   * Returns an updated initializer after joining with given assignment
   * @param var variable which initializer should be updated
   * @param assignment assignment to merge into the initializer
   * @return updated initializer or null if operation cannot be performed (e.g. code is incomplete)
   */
  @Nullable
  public static PsiExpression getInitializerExpression(PsiLocalVariable var,
                                                       PsiAssignmentExpression assignment) {
    return getInitializerExpression(var.getInitializer(), assignment);
  }

  @Nullable
  public static PsiExpression getInitializerExpression(PsiExpression initializer, PsiAssignmentExpression assignment) {
    PsiExpression initializerExpression;
    PsiJavaToken sign = assignment.getOperationSign();
    final IElementType compoundOp = assignment.getOperationTokenType();
    final PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return null;
    if (compoundOp == JavaTokenType.EQ) {
      return rExpression;
    }
    if (initializer == null) return null;
    String opSign = sign.getText().replace("=", "");
    IElementType simpleOp = TypeConversionUtil.convertEQtoOperation(compoundOp);
    if (simpleOp == null) return null;
    final Project project = assignment.getProject();
    final String rightText = rExpression.getText();
    String initializerText;
    if (isIdentity(initializer, opSign)) {
      initializerText = rightText;
    } else if (isIdentity(rExpression, opSign)) {
      initializerText = initializer.getText();
    } else {
      boolean parenthesesForLhs = PsiPrecedenceUtil.getPrecedence(initializer) > PsiPrecedenceUtil.getPrecedenceForOperator(simpleOp);
      boolean parenthesesForRhs = PsiPrecedenceUtil.areParenthesesNeeded(sign, rExpression);
      initializerText = (parenthesesForLhs ? "(" + initializer.getText() + ")" : initializer.getText()) + opSign +
                        (parenthesesForRhs ? "(" + rExpression.getText() + ")" : rExpression.getText());
    }
    initializerExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(initializerText, assignment);
    return (PsiExpression)CodeStyleManager.getInstance(project).reformat(initializerExpression);
  }

  private static boolean isIdentity(PsiExpression operand, String opSign) {
    return "+".equals(opSign) && ExpressionUtils.isZero(operand) ||
           "*".equals(opSign) && ExpressionUtils.isOne(operand) ||
           "^".equals(opSign) && ExpressionUtils.isLiteral(operand, false) ||
           "|".equals(opSign) && ExpressionUtils.isLiteral(operand, false) ||
           "&".equals(opSign) && ExpressionUtils.isLiteral(operand, true);
  }

  @Nullable
  public static PsiLocalVariable copyVarWithInitializer(PsiLocalVariable origVar, PsiExpression initializer) {
    // Don't normalize the original declaration: it may declare many variables
    PsiElement declCopy = origVar.getParent().copy();
    PsiLocalVariable varCopy = (PsiLocalVariable)ContainerUtil.find(
      declCopy.getChildren(), e -> e instanceof PsiLocalVariable && Objects.equals(origVar.getName(), ((PsiLocalVariable)e).getName()));

    if (varCopy != null) {
      varCopy.setInitializer(initializer);
      varCopy.normalizeDeclaration();
    }
    return varCopy;
  }

  /**
   * Join declaration and assignment
   * @param variable variable
   * @param assignment assignment (assuming its parent is expression statement)
   * @return new variable
   */
  public static PsiLocalVariable joinDeclarationAndAssignment(@NotNull PsiLocalVariable variable, @NotNull PsiAssignmentExpression assignment) {
    PsiExpression initializer = getInitializerExpression(variable, assignment);
    PsiElement elementToReplace = assignment.getParent();
    if (elementToReplace != null) {
      PsiLocalVariable varCopy = copyVarWithInitializer(variable, initializer);
      if (varCopy != null) {
        String text = varCopy.getText();

        CommentTracker tracker = new CommentTracker();
        tracker.markUnchanged(initializer);
        tracker.markUnchanged(variable);
        tracker.delete(variable);
        PsiDeclarationStatement decl = (PsiDeclarationStatement)tracker.replaceAndRestoreComments(elementToReplace, text);
        return ((PsiLocalVariable)decl.getDeclaredElements()[0]);
      }
    }
    return variable;
  }
}

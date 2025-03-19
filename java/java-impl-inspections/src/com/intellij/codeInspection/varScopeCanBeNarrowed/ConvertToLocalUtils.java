// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.varScopeCanBeNarrowed;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.CommonJavaInlineUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * @author Bas Leijdekkers
 */
final class ConvertToLocalUtils {

  @Nullable
  public static PsiElement copyVariableToMethodBody(PsiVariable variable, List<? extends PsiReferenceExpression> references,
                                                    Function<? super PsiCodeBlock, String> newName) {
    final PsiCodeBlock anchorBlock = findAnchorBlock(references);
    if (anchorBlock == null) return null; // was assertion, but need to fix the case when obsolete inspection highlighting is left
    final PsiElement firstElement = getLowestOffsetElement(references);
    final String localName = newName.apply(anchorBlock);
    final PsiElement anchor = getAnchorElement(anchorBlock, firstElement);
    if (anchor == null) return null;
    final PsiAssignmentExpression anchorAssignmentExpression = searchAssignmentExpression(anchor);
    final PsiExpression initializer = anchorAssignmentExpression != null && isVariableAssignment(anchorAssignmentExpression, variable)
                                      ? anchorAssignmentExpression.getRExpression()
                                      : variable.getInitializer();
    final PsiElementFactory psiFactory = JavaPsiFacade.getElementFactory(variable.getProject());
    final PsiDeclarationStatement declaration =
      psiFactory.createVariableDeclarationStatement(localName, variable.getType(), initializer, variable);
    for (int i = 1; i < references.size(); i++) {
      if (PsiUtil.isAccessedForWriting(references.get(i))) {
        PsiUtil.setModifierProperty((PsiLocalVariable)declaration.getDeclaredElements()[0], PsiModifier.FINAL, false);
        break;
      }
    }
    final PsiElement newDeclaration;
    if (anchorAssignmentExpression != null && isVariableAssignment(anchorAssignmentExpression, variable)) {
      newDeclaration = new CommentTracker().replaceAndRestoreComments(anchor, declaration);
    }
    else if (anchorBlock.getParent() instanceof PsiSwitchStatement) {
      PsiElement parent = anchorBlock.getParent();
      PsiElement switchContainer = parent.getParent();
      newDeclaration = switchContainer.addBefore(declaration, parent);
    }
    else {
      newDeclaration = anchorBlock.addBefore(declaration, anchor);
    }
    retargetReferences(psiFactory, localName, references);
    return newDeclaration;
  }

  public static void inlineRedundant(@Nullable PsiElement declaration) {
    final PsiLocalVariable newVariable = extractDeclared(declaration);
    if (newVariable != null) {
      final PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(newVariable.getInitializer());
      if (VariableAccessUtils.isLocalVariableCopy(newVariable, initializer)) {
        final List<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(newVariable);
        for (PsiJavaCodeReferenceElement reference : references) {
          CommonJavaInlineUtil.getInstance().inlineVariable(newVariable, initializer, reference, null);
        }
        declaration.delete();
      }
    }
  }

  private static @Nullable PsiLocalVariable extractDeclared(@Nullable PsiElement declaration) {
    if (!(declaration instanceof PsiDeclarationStatement statement)) return null;
    final PsiElement[] declaredElements = statement.getDeclaredElements();
    return declaredElements.length == 1 && declaredElements[0] instanceof PsiLocalVariable var ? var : null;
  }

  private static @Nullable PsiAssignmentExpression searchAssignmentExpression(@Nullable PsiElement anchor) {
    if (!(anchor instanceof PsiExpressionStatement statement)) return null;
    return statement.getExpression() instanceof PsiAssignmentExpression assignment ? assignment : null;
  }

  private static boolean isVariableAssignment(@NotNull PsiAssignmentExpression expression, @NotNull PsiVariable variable) {
    return expression.getOperationTokenType() == JavaTokenType.EQ &&
           expression.getLExpression() instanceof PsiReferenceExpression leftExpression &&
           leftExpression.isReferenceTo(variable);
  }

  private static void retargetReferences(PsiElementFactory elementFactory,
                                         String newName,
                                         Collection<? extends PsiReferenceExpression> refs) {
    final PsiReferenceExpression newRef = (PsiReferenceExpression)elementFactory.createExpressionFromText(newName, null);
    for (PsiReferenceExpression ref : refs) {
      ref.replace(newRef);
    }
  }

  private static @Nullable PsiElement getAnchorElement(PsiCodeBlock anchorBlock, @Nullable PsiElement firstElement) {
    PsiElement element = firstElement;
    while (element != null && element.getParent() != anchorBlock) {
      element = element.getParent();
    }
    return element;
  }

  private static @Nullable PsiElement getLowestOffsetElement(@NotNull Collection<? extends PsiReferenceExpression> refs) {
    PsiElement firstElement = null;
    for (PsiReferenceExpression reference : refs) {
      if (firstElement == null || firstElement.getTextRange().getStartOffset() > reference.getTextRange().getStartOffset()) {
        firstElement = reference;
      }
    }
    return firstElement;
  }

  private static PsiCodeBlock findAnchorBlock(Collection<? extends PsiReferenceExpression> refs) {
    PsiCodeBlock result = null;
    for (PsiReferenceExpression psiReference : refs) {
      if (PsiUtil.isInsideJavadocComment(psiReference)) continue;
      PsiCodeBlock block = PsiTreeUtil.getParentOfType(psiReference, PsiCodeBlock.class);
      if (result == null || block == null) {
        result = block;
      }
      else {
        final PsiElement commonParent = PsiTreeUtil.findCommonParent(result, block);
        result = PsiTreeUtil.getParentOfType(commonParent, PsiCodeBlock.class, false);
        if (result == null) return null;
      }
    }
    return result;
  }
}
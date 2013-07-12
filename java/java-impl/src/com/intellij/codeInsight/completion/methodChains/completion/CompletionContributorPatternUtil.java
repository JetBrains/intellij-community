package com.intellij.codeInsight.completion.methodChains.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public final class CompletionContributorPatternUtil {

  private CompletionContributorPatternUtil() {}

  @SuppressWarnings("unchecked")
  public static ElementPattern<PsiElement> patternForVariableAssignment() {
    final ElementPattern<PsiElement> patternForParent = or(psiElement().withText(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
                                                             .afterSiblingSkipping(psiElement(PsiWhiteSpace.class),
                                                                                   psiElement(PsiJavaToken.class).withText("=")));

    return psiElement().withParent(patternForParent).withSuperParent(2, or(psiElement(PsiAssignmentExpression.class),
                                                                           psiElement(PsiLocalVariable.class)
                                                                             .inside(PsiDeclarationStatement.class)))
                                                                             .inside(PsiMethod.class);
  }

  public static ElementPattern<PsiElement> patternForMethodParameter() {
    return psiElement().withSuperParent(3, PsiMethodCallExpressionImpl.class);
  }
}

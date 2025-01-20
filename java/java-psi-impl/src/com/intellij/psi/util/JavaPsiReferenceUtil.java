// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public final class JavaPsiReferenceUtil {
  private JavaPsiReferenceUtil() { }

  /**
   * @param expression      reference that potentially references the field
   * @param referencedField the field referenced
   * @param acceptQualified true if qualified references are accepted
   * @return {@link ThreeState#UNSURE} if
   */
  public static @NotNull ForwardReferenceProblem checkForwardReference(@NotNull PsiReferenceExpression expression,
                                                                       @NotNull PsiField referencedField,
                                                                       boolean acceptQualified) {
    PsiClass containingClass = referencedField.getContainingClass();
    if (containingClass == null) return ForwardReferenceProblem.LEGAL;
    if (expression.getContainingFile() != referencedField.getContainingFile()) return ForwardReferenceProblem.LEGAL;
    TextRange fieldRange = referencedField.getTextRange();
    if (fieldRange == null || expression.getTextRange().getStartOffset() >= fieldRange.getEndOffset()) return ForwardReferenceProblem.LEGAL;
    if (!acceptQualified) {
      if (containingClass.isEnum()) {
        if (isLegalForwardReferenceInEnum(expression, referencedField, containingClass)) return ForwardReferenceProblem.LEGAL;
      }
      // simple reference can be illegal (JLS 8.3.3)
      else if (expression.getQualifierExpression() != null) return ForwardReferenceProblem.LEGAL;
    }
    PsiField initField = findEnclosingFieldInitializer(expression);
    PsiClassInitializer classInitializer = findParentClassInitializer(expression);
    if (initField == null && classInitializer == null) return ForwardReferenceProblem.LEGAL;
    // instance initializers may access static fields
    boolean isStaticClassInitializer = classInitializer != null && classInitializer.hasModifierProperty(PsiModifier.STATIC);
    boolean isStaticInitField = initField != null && initField.hasModifierProperty(PsiModifier.STATIC);
    boolean inStaticContext = isStaticInitField || isStaticClassInitializer;
    if (!inStaticContext && referencedField.hasModifierProperty(PsiModifier.STATIC)) return ForwardReferenceProblem.LEGAL;
    if (PsiUtil.isOnAssignmentLeftHand(expression) && !PsiUtil.isAccessedForReading(expression)) return ForwardReferenceProblem.LEGAL;
    if (!containingClass.getManager().areElementsEquivalent(containingClass, PsiTreeUtil.getParentOfType(expression, PsiClass.class))) {
      return ForwardReferenceProblem.LEGAL;
    }
    return initField == referencedField
           ? ForwardReferenceProblem.ILLEGAL_SELF_REFERENCE
           : ForwardReferenceProblem.ILLEGAL_FORWARD_REFERENCE;
  }

  private static boolean isLegalForwardReferenceInEnum(@NotNull PsiReferenceExpression expression,
                                                       @NotNull PsiField referencedField,
                                                       @NotNull PsiClass containingClass) {
    PsiExpression qualifierExpr = expression.getQualifierExpression();
    // simple reference can be illegal (JLS 8.3.3)
    if (qualifierExpr == null) return false;
    if (!(qualifierExpr instanceof PsiReferenceExpression)) return true;

    PsiElement qualifiedReference = ((PsiReferenceExpression)qualifierExpr).resolve();
    if (containingClass.equals(qualifiedReference)) {
      // static fields that are constant variables (4.12.4) are initialized before other static fields (12.4.2),
      // so a qualified reference to the constant variable is possible.
      return PsiUtil.isCompileTimeConstant(referencedField);
    }
    return true;
  }

  /**
   * @return field that has initializer with this element as subexpression or null if not found
   */
  static PsiField findEnclosingFieldInitializer(@NotNull PsiElement entry) {
    PsiElement element = entry;
    while (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiField) {
        if (element == ((PsiField)parent).getInitializer()) return (PsiField)parent;
        if (parent instanceof PsiEnumConstant && element == ((PsiEnumConstant)parent).getArgumentList()) return (PsiField)parent;
      }
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = parent;
    }
    return null;
  }

  private static PsiClassInitializer findParentClassInitializer(@NotNull PsiElement root) {
    PsiElement element = root;
    while (element != null) {
      if (element instanceof PsiClassInitializer) return (PsiClassInitializer)element;
      if (element instanceof PsiClass || element instanceof PsiMethod) return null;
      element = element.getParent();
    }
    return null;
  }

  /**
   * A kind of problem with forward reference
   */
  public enum ForwardReferenceProblem {
    /**
     * The reference has no problem in terms of forward kind
     */
    LEGAL,
    /**
     * The reference refers to an object being initialized (e.g., field initializer refers to a field itself)
     */
    ILLEGAL_SELF_REFERENCE,
    /**
     * The reference is an illegal reference to an object declared later
     */
    ILLEGAL_FORWARD_REFERENCE
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class CheckInitialized implements ElementFilter {
  private final Set<PsiField> myNonInitializedFields;
  private final boolean myInsideConstructorCall;

  CheckInitialized(@NotNull PsiElement position) {
    myNonInitializedFields = getNonInitializedFields(position);
    myInsideConstructorCall = isInsideConstructorCall(position);
  }

  static boolean isInsideConstructorCall(@NotNull PsiElement position) {
    return JavaPsiConstructorUtil.isConstructorCall(PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class)) &&
           !JavaKeywordCompletion.AFTER_DOT.accepts(position);
  }

  private static boolean isInitializedImplicitly(PsiField field) {
    field = CompletionUtil.getOriginalOrSelf(field);
    for(ImplicitUsageProvider provider: ImplicitUsageProvider.EP_NAME.getExtensionList()) {
      if (provider.isImplicitWrite(field)) {
        return true;
      }
    }
    return false;
  }

  static Set<PsiField> getNonInitializedFields(PsiElement element) {
    PsiReferenceExpression ref = PsiTreeUtil.getNonStrictParentOfType(element, PsiReferenceExpression.class);
    if (ref == null) return Collections.emptySet();

    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    //noinspection SSBasedInspection
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiClass.class);
    if (statement == null || method == null || !method.isConstructor()) {
      return Collections.emptySet();
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression && !ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)parent)) {
      return Collections.emptySet();
    }

    while (parent != statement) {
      PsiElement next = parent.getParent();
      if (next instanceof PsiAssignmentExpression && parent == ((PsiAssignmentExpression)next).getLExpression()) {
        return Collections.emptySet();
      }
      if (parent instanceof PsiJavaCodeReferenceElement) {
        PsiStatement psiStatement = PsiTreeUtil.getParentOfType(parent, PsiStatement.class);
        if (psiStatement != null && psiStatement.getTextRange().getStartOffset() == parent.getTextRange().getStartOffset()) {
          return Collections.emptySet();
        }
      }
      parent = next;
    }

    boolean allowNonFinalFields = !isInsideConstructorCall(element);

    final Set<PsiField> fields = new HashSet<>();
    final PsiClass containingClass = method.getContainingClass();
    assert containingClass != null;
    InheritanceUtil.processSupers(containingClass, true, eachClass -> {
      for (PsiField field : eachClass.getFields()) {
        if (!seemsInitialized(allowNonFinalFields, containingClass, field)) {
          fields.add(field);
        }
      }
      return true;
    });

    Set<PsiField> assigned = new HashSet<>();
    method.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        PsiExpression lExpression = expression.getLExpression();
        if (lExpression instanceof PsiReferenceExpression && ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)lExpression)) {
          PsiElement target = ((PsiReferenceExpression)lExpression).resolve();
          if (target instanceof PsiField) {
            if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
              assigned.add((PsiField)target);
              fields.remove(target);
            }
            else if (ref == PsiUtil.deparenthesizeExpression(expression.getRExpression()) && !assigned.contains(target)) {
              fields.add((PsiField)target);
            }
          }
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        if (expression.getTextRange().getStartOffset() < statement.getTextRange().getStartOffset()) {
          final PsiReferenceExpression methodExpression = expression.getMethodExpression();
          if (methodExpression.textMatches(JavaKeywords.THIS)) {
            fields.clear();
          }
        }
        super.visitMethodCallExpression(expression);
      }
    });
    return fields;
  }

  private static boolean seemsInitialized(boolean allowNonFinalFields, PsiClass placeClass, PsiField field) {
    if (field.hasModifierProperty(PsiModifier.STATIC)) return true;
    if (field.getContainingClass() == placeClass) {
      if (isInitializedBeforeConstructor(field, placeClass) ||
          isInitializedImplicitly(field)) {
        return true;
      }
    }
    return allowNonFinalFields && !field.hasModifierProperty(PsiModifier.FINAL);
  }

  private static boolean isInitializedBeforeConstructor(PsiField field, PsiClass containingClass) {
    if (field.getInitializer() != null) return true;

    return ContainerUtil.exists(containingClass.getInitializers(), i -> 
      !i.hasModifierProperty(PsiModifier.STATIC) && VariableAccessUtils.variableIsAssigned(field, i, false));
  }

  @Override
  public boolean isAcceptable(Object element, @Nullable PsiElement context) {
    PsiMethod method = MethodParameterInfoHandler.tryGetMethodFromCandidate(element);
    if (method != null) {
      element = method;
    }
    if (element instanceof PsiField) {
      return !myNonInitializedFields.contains(element);
    }
    if (element instanceof PsiMethod && myInsideConstructorCall) {
      return ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC);
    }

    return true;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}

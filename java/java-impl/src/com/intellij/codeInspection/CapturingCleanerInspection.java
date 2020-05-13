// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static com.intellij.util.ObjectUtils.tryCast;

public class CapturingCleanerInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher CLEANER_REGISTER = CallMatcher.instanceCall(
    "java.lang.ref.Cleaner", "register"
  ).parameterCount(2);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel9OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!CLEANER_REGISTER.test(call)) return;
        PsiExpression[] expressions = call.getArgumentList().getExpressions();
        PsiExpression trackedObject = ExpressionUtils.resolveExpression(expressions[0]);
        PsiExpression runnableExpression = ExpressionUtils.resolveExpression(expressions[1]);
        if (trackedObject == null || runnableExpression == null) return;

        final PsiElement highlightingElement;
        final String referenceName;
        if (trackedObject instanceof PsiThisExpression) {
          PsiClassType classType = tryCast(trackedObject.getType(), PsiClassType.class);
          if (classType == null) return;
          PsiClass trackedClass = classType.resolve();
          if (trackedClass == null) return;
          PsiElement elementCapturingThis = getElementCapturingThis(runnableExpression, trackedClass);
          if (elementCapturingThis == null) return;
          highlightingElement = elementCapturingThis;
          referenceName = "this";
        }
        else if (trackedObject instanceof PsiReferenceExpression) {
          PsiVariable variable = tryCast(((PsiReferenceExpression)trackedObject).resolve(), PsiVariable.class);
          if (variable == null) return;
          if (variable instanceof PsiField) return;
          if (!VariableAccessUtils.variableIsUsed(variable, runnableExpression)) return;
          Optional<PsiElement> referenceExpression = StreamEx.ofTree(((PsiElement)runnableExpression), el -> StreamEx.of(el.getChildren()))
            .findAny(el -> el instanceof PsiReferenceExpression &&
                           ((PsiReferenceExpression)el).isReferenceTo(variable));
          if (!referenceExpression.isPresent()) return;
          String variableName = variable.getName();
          if (variableName == null) return;
          highlightingElement = referenceExpression.get();
          referenceName = variableName;
        }
        else {
          return;
        }
        holder.registerProblem(highlightingElement, JavaBundle.message("inspection.capturing.cleaner", referenceName));
      }


      @Nullable
      private PsiElement getElementCapturingThis(@NotNull PsiExpression runnableExpr,
                                                 @NotNull PsiClass trackedClass) {
        if (runnableExpr instanceof PsiMethodReferenceExpression) {
          PsiMethodReferenceExpression methodReference = (PsiMethodReferenceExpression)runnableExpr;
          if (PsiMethodReferenceUtil.isStaticallyReferenced(methodReference)) return null;

          PsiElement qualifier = methodReference.getQualifier();
          if (qualifier instanceof PsiThisExpression) {
            PsiClass thisClass = PsiUtil.resolveClassInType(((PsiThisExpression)qualifier).getType());
            if (thisClass != trackedClass) return null;
            return qualifier;
          }
          return null;
        }
        if (runnableExpr instanceof PsiLambdaExpression) {
          PsiLambdaExpression lambda = (PsiLambdaExpression)runnableExpr;
          if (!lambda.getParameterList().isEmpty()) return null;
          PsiElement lambdaBody = lambda.getBody();
          if (lambdaBody == null) return null;
          return getLambdaElementCapturingThis(lambdaBody, trackedClass).orElse(null);
        }
        if (runnableExpr instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)runnableExpr;
          if (newExpression.getAnonymousClass() != null) return newExpression;
          PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
          if (classReference == null) return null;
          PsiClass aClass = tryCast(classReference.resolve(), PsiClass.class);
          if (aClass == null) return null;
          if (aClass.getContainingClass() != trackedClass) return null;
          if (aClass.hasModifierProperty(PsiModifier.STATIC)) return null;
          return classReference;
        }
        return null;
      }
    };
  }

  private static Optional<PsiElement> getLambdaElementCapturingThis(@NotNull PsiElement lambdaBody, @NotNull PsiClass containingClass) {
    return StreamEx.ofTree(lambdaBody, el -> StreamEx.of(el.getChildren()))
      .findAny(element -> isThisCapturingElement(containingClass, element));
  }

  private static boolean isThisCapturingElement(@NotNull PsiClass containingClass, PsiElement element) {
    if (element instanceof PsiThisExpression) {
      return PsiUtil.resolveClassInType(((PsiThisExpression)element).getType()) == containingClass;
    }
    else if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression qualifierReference =
        tryCast(((PsiReferenceExpression)element).getQualifierExpression(), PsiReferenceExpression.class);
      if (qualifierReference != null) return false;
      PsiMember member = tryCast(((PsiReferenceExpression)element).resolve(), PsiMember.class);
      return memberBringsThisRef(containingClass, member);
    }
    return false;
  }

  @Contract("_, null -> false")
  private static boolean memberBringsThisRef(@NotNull PsiClass containingClass, PsiMember member) {
    if (member == null) return false;
    PsiClass memberContainingClass = member.getContainingClass();
    if (memberContainingClass == null) return false;
    if (!InheritanceUtil.isInheritorOrSelf(containingClass, memberContainingClass, true) &&
        !isInnerClassOf(containingClass, memberContainingClass)) {
      return false;
    }
    return !member.hasModifierProperty(PsiModifier.STATIC);
  }

  @Contract("_, null -> false")
  private static boolean isInnerClassOf(@Nullable PsiClass inner, @Nullable PsiClass outer) {
    if (inner == null || inner.hasModifierProperty(PsiModifier.STATIC)) return false;
    return PsiTreeUtil.isAncestor(outer, inner, false);
  }
}

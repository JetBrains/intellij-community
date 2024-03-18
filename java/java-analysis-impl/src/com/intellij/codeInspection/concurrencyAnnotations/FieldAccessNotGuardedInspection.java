// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FieldAccessNotGuardedInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.concurrency.annotation.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "FieldAccessNotGuarded";
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiSynchronizedStatement) {
        return;
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField) && !(referent instanceof PsiMethod)) {
        return;
      }
      final PsiMember member = (PsiMember)referent;
      final String guard = JCiPUtil.findGuardForMember(member);
      if (guard == null) {
        return;
      }
      final PsiExpression guardExpression;
      try {
        guardExpression = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(guard, member);
      } catch (IncorrectOperationException ignore) {
        return;
      }
      if (guardExpression instanceof PsiThisExpression && !PsiUtil.isAccessedForWriting(expression) &&
          member.hasModifierProperty(PsiModifier.VOLATILE)) {
        return;
      }
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod != null) {
        if (JCiPUtil.isGuardedBy(containingMethod, guard) || containingMethod.isConstructor()) {
          return;
        }
        if (containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          if (guardExpression instanceof PsiThisExpression thisExpression) {
            final PsiClass aClass = getClassFromThisExpression(thisExpression, member);
            if (aClass == null || InheritanceUtil.isInheritorOrSelf(containingMethod.getContainingClass(), aClass, true)) {
              return;
            }
          }
          else if (containingMethod.hasModifierProperty(PsiModifier.STATIC) && guardExpression instanceof PsiClassObjectAccessExpression) {
            PsiClass psiClass = PsiUtil.resolveClassInType(((PsiClassObjectAccessExpression)guardExpression).getOperand().getType());
            if (psiClass == null || psiClass.equals(containingMethod.getContainingClass())) {
              return;
            }
          }
        }
      }

      if (findLockTryStatement(expression, guard) != null) {
        PsiElement lockExpr = expression;
        while (lockExpr != null) {
          PsiElement child = lockExpr;
          while (child != null) {
            if (isLockGuardStatement(guard, child, "lock")) return;
            final PsiElement childParent = child.getParent();
            if (child instanceof PsiMethodCallExpression &&
                isCallOnGuard(guard, "tryLock", (PsiMethodCallExpression)child) &&
                childParent instanceof PsiIfStatement &&
                ((PsiIfStatement)childParent).getCondition() == child) {
              return;
            }
            child = child.getPrevSibling();
          }
          lockExpr = lockExpr.getParent();
        }
      }

      PsiElement check = expression;
      while (true) {
        final PsiSynchronizedStatement syncStatement = PsiTreeUtil.getParentOfType(check, PsiSynchronizedStatement.class);
        if (syncStatement == null) {
          break;
        }
        final PsiExpression lockExpression = PsiUtil.skipParenthesizedExprDown(syncStatement.getLockExpression());
        if (lockExpression == null) {
          continue;
        }
        if (guardExpression instanceof PsiThisExpression thisExpression1) {
          if (lockExpression instanceof PsiThisExpression thisExpression2) {
            final PsiClass aClass1 = getClassFromThisExpression(thisExpression1, member);
            final PsiClass aClass2 = getClassFromThisExpression(thisExpression2, expression);
            if (aClass1 == null || aClass1.equals(aClass2)) {
              return;
            }
          }
          else if (lockExpression instanceof PsiReferenceExpression) {
            final PsiExpression qualifierExpression = expression.getQualifierExpression();
            if (qualifierExpression != null && PsiEquivalenceUtil.areElementsEquivalent(lockExpression, qualifierExpression)) {
              return;
            }
          }
        }
        else if (guardExpression instanceof PsiReferenceExpression referenceExpression1 &&
                 lockExpression instanceof PsiReferenceExpression referenceExpression2) {
          final PsiElement target1 = referenceExpression1.resolve();
          final PsiElement target2 = referenceExpression2.resolve();
          if (target1 == null || target1.equals(target2)) {
            final PsiExpression lockQualifier = referenceExpression2.getQualifierExpression();
            if (referenceExpression1.getQualifierExpression() == null && lockQualifier != null) {
              final PsiExpression qualifierExpression = expression.getQualifierExpression();
              if (qualifierExpression != null && PsiEquivalenceUtil.areElementsEquivalent(lockQualifier, qualifierExpression)) {
                return;
              }
            } else {
              return;
            }
          }
        }
        else if (guardExpression instanceof PsiMethodCallExpression methodCallExpression1 &&
                 lockExpression instanceof PsiMethodCallExpression methodCallExpression2) {
          if (methodCallExpression2.getArgumentList().isEmpty()) {
            final PsiMethod method1 = methodCallExpression1.resolveMethod();
            final PsiMethod method2 = methodCallExpression2.resolveMethod();
            if (method1 == null || method1.equals(method2)) {
              final PsiReferenceExpression methodExpression2 = methodCallExpression2.getMethodExpression();
              final PsiExpression qualifierExpression1 = expression.getQualifierExpression();
              final PsiExpression qualifierExpression2 = methodExpression2.getQualifierExpression();
              if (qualifierExpression1 == null && qualifierExpression2 == null) {
                return;
              }
              if (qualifierExpression1 != null && qualifierExpression2 != null &&
                  PsiEquivalenceUtil.areElementsEquivalent(qualifierExpression1, qualifierExpression2)) {
                return;
              }
            }
          }
        }
        else if (guardExpression instanceof PsiClassObjectAccessExpression classObjectAccessExpression1 &&
                 lockExpression instanceof PsiClassObjectAccessExpression classObjectAccessExpression2) {
          final PsiClass aClass1 = PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression1.getOperand().getType());
          final PsiClass aClass2 = PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression2.getOperand().getType());
          if (aClass1 == null || aClass1.equals(aClass2)) {
            return;
          }
        }
        check = syncStatement;
      }
      myHolder.registerProblem(expression,
                               member instanceof PsiField ?
                               JavaAnalysisBundle.message("access.to.field.code.ref.code.outside.of.declared.guards.loc") :
                               JavaAnalysisBundle.message("call.to.method.code.ref.code.outside.of.declared.guards.loc"));
    }

    private static PsiClass getClassFromThisExpression(PsiThisExpression thisExpression, PsiElement context) {
      final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
      if (qualifier == null) {
        return PsiTreeUtil.getParentOfType(context, PsiClass.class);
      }
      else {
        final PsiElement target = qualifier.resolve();
        return target instanceof PsiClass ? (PsiClass)target : null;
      }
    }

    @Nullable
    private static PsiTryStatement findLockTryStatement(PsiReferenceExpression expression, String guard) {
      PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(expression, PsiTryStatement.class);
      while (tryStatement != null) {
        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
          for (PsiStatement psiStatement : finallyBlock.getStatements()) {
            if (isLockGuardStatement(guard, psiStatement, "unlock")) {
              return tryStatement;
            }
          }
        }
        tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class);
      }
      return null;
    }

    private static boolean isLockGuardStatement(String guard, PsiElement element, final String lockMethodStart) {
      if (element instanceof PsiExpressionStatement) {
        final PsiExpression psiExpression = ((PsiExpressionStatement)element).getExpression();
        if (psiExpression instanceof PsiMethodCallExpression) {
          return isCallOnGuard(guard, lockMethodStart, (PsiMethodCallExpression)psiExpression);
        }
      }
      return false;
    }

    private static boolean isCallOnGuard(String guard, String lockMethodStart, PsiMethodCallExpression psiExpression) {
      final PsiReferenceExpression methodExpression = psiExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        if (isCallOnGuard(guard, lockMethodStart, methodExpression, qualifierExpression)) {
          return true;
        } else if (qualifierExpression instanceof PsiReferenceExpression) {
          final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
          if (resolve instanceof PsiField && ((PsiField)resolve).hasModifierProperty(PsiModifier.FINAL)) {
            final PsiExpression initializer = ((PsiField)resolve).getInitializer();
            return initializer != null && isCallOnGuard(guard, lockMethodStart, methodExpression, initializer);
          }
        }
      }
      return false;
    }

    private static boolean isCallOnGuard(String guard,
                                         String lockMethodStart,
                                         PsiReferenceExpression methodExpression,
                                         PsiExpression qualifier) {
      final String qualifierText = qualifier.getText();
      if (qualifierText.startsWith(guard + ".") || qualifierText.equals(guard)) {
        final PsiElement resolve = methodExpression.resolve();
        if (resolve instanceof PsiMethod) {
          final String methodName = ((PsiMethod)resolve).getName();
          if (methodName.startsWith(lockMethodStart)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldAccessNotGuardedInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CONCURRENCY_ANNOTATION_ISSUES;
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unguarded field access";
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

    public Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiSynchronizedStatement) {
        return;
      }
      final PsiElement referent = expression.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      final String guard = JCiPUtil.findGuardForMember(field);
      if (guard == null) {
        return;
      }
      final PsiExpression guardExpression;
      try {
        guardExpression = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(guard, field);
      } catch (IncorrectOperationException ignore) {
        return;
      }
      if (guardExpression instanceof PsiThisExpression && !PsiUtil.isAccessedForWriting(expression) &&
          field.hasModifierProperty(PsiModifier.VOLATILE)) {
        return;
      }
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod != null) {
        if (JCiPUtil.isGuardedBy(containingMethod, guard) || containingMethod.isConstructor()) {
          return;
        }
        if (containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          if (guardExpression instanceof PsiThisExpression) {
            final PsiThisExpression thisExpression = (PsiThisExpression)guardExpression;
            final PsiClass aClass = getClassFromThisExpression(thisExpression, field);
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
        if (guardExpression instanceof PsiThisExpression) {
          if (lockExpression instanceof PsiThisExpression) {
            final PsiThisExpression thisExpression1 = (PsiThisExpression)guardExpression;
            final PsiThisExpression thisExpression2 = (PsiThisExpression)lockExpression;
            final PsiClass aClass1 = getClassFromThisExpression(thisExpression1, field);
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
        else if (guardExpression instanceof PsiReferenceExpression && lockExpression instanceof PsiReferenceExpression) {
          final PsiReferenceExpression referenceExpression1 = (PsiReferenceExpression)guardExpression;
          final PsiReferenceExpression referenceExpression2 = (PsiReferenceExpression)lockExpression;
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
        else if (guardExpression instanceof PsiMethodCallExpression && lockExpression instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression methodCallExpression1 = (PsiMethodCallExpression)guardExpression;
          final PsiMethodCallExpression methodCallExpression2 = (PsiMethodCallExpression)lockExpression;
          if (methodCallExpression2.getArgumentList().getExpressions().length == 0) {
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
        else if (guardExpression instanceof PsiClassObjectAccessExpression && lockExpression instanceof PsiClassObjectAccessExpression) {
          final PsiClassObjectAccessExpression classObjectAccessExpression1 = (PsiClassObjectAccessExpression)guardExpression;
          final PsiClassObjectAccessExpression classObjectAccessExpression2 = (PsiClassObjectAccessExpression)lockExpression;
          final PsiClass aClass1 = PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression1.getOperand().getType());
          final PsiClass aClass2 = PsiUtil.resolveClassInClassTypeOnly(classObjectAccessExpression2.getOperand().getType());
          if (aClass1 == null || aClass1.equals(aClass2)) {
            return;
          }
        }
        check = syncStatement;
      }
      myHolder.registerProblem(expression, "Access to field <code>#ref</code> outside of declared guards #loc");
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
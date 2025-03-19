// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.refactoring.safeDelete.usageInfo.*;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Max Medvedev
 */
public final class JavaSafeDeleteDelegateImpl implements JavaSafeDeleteDelegate {
  @Override
  public void createUsageInfoForParameter(@NotNull PsiReference reference,
                                          @NotNull List<? super UsageInfo> usages,
                                          @NotNull PsiNamedElement parameter,
                                          int paramIdx,
                                          boolean isVararg) {
    final PsiElement element = reference.getElement();
    PsiCall call = null;
    if (element instanceof PsiCall) {
      call = (PsiCall)element;
    }
    else {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiCall) {
        call = (PsiCall)parent;
      }
      else if (parent instanceof PsiAnonymousClass) {
        call = (PsiNewExpression)parent.getParent();
      }
    }
    if (call != null) {
      final PsiExpressionList argList = call.getArgumentList();
      if (argList != null) {
        final PsiExpression[] args = argList.getExpressions();
        if (paramIdx < args.length) {
          if (!isVararg) {
            usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args[paramIdx], parameter));
          }
          else {
            for (int i = paramIdx; i < args.length; i++) {
              usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args[i], parameter));
            }
          }
        }
      }
    }
    else if (element instanceof PsiDocMethodOrFieldRef) {
      String[] signature = ((PsiDocMethodOrFieldRef)element).getSignature();
      PsiElement nameElement = ((PsiDocMethodOrFieldRef)element).getNameElement();
      if (signature != null && nameElement != null) {
        final @NonNls StringBuffer newText = new StringBuffer();
        newText.append("/** @see #").append(nameElement.getText()).append('(');
        boolean hasParams = false;
        for (int i = 0; i < signature.length; i++) {
          if (i == paramIdx) continue;
          if (hasParams) {
            newText.append(",");
          }
          else {
            hasParams = true;
          }
          newText.append(signature[i]);
        }
        newText.append(")*/");
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
          @Override
          public void deleteElement() throws IncorrectOperationException {
            final PsiDocMethodOrFieldRef.MyReference javadocMethodReference = (PsiDocMethodOrFieldRef.MyReference)element.getReference();
            if (javadocMethodReference != null) {
              javadocMethodReference.bindToText(newText);
            }
          }
        });
      }
    }
    else if (element instanceof PsiMethodReferenceExpression) {
      usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
        @Override
        public void deleteElement() throws IncorrectOperationException {
          final PsiExpression callExpression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)element);
          if (callExpression instanceof PsiCallExpression) {
            final PsiExpressionList expressionList = ((PsiCallExpression)callExpression).getArgumentList();
            if (expressionList != null) {
              final PsiExpression[] args = expressionList.getExpressions();
              if (paramIdx < args.length) {
                args[paramIdx].delete();
              }
            }
          }
        }
      });
    }
  }

  @Override
  public void createJavaTypeParameterUsageInfo(@NotNull PsiReference reference,
                                               @NotNull List<? super UsageInfo> usages,
                                               @NotNull PsiElement typeParameter,
                                               int paramsCount,
                                               int index)  {
    if (reference instanceof PsiJavaCodeReferenceElement) {
      final PsiReferenceParameterList parameterList = ((PsiJavaCodeReferenceElement)reference).getParameterList();
      if (parameterList != null) {
        PsiTypeElement[] typeArgs = parameterList.getTypeParameterElements();
        if (typeArgs.length > index) {
          if (typeArgs.length == 1 && paramsCount > 1 && typeArgs[0].getType() instanceof PsiDiamondType) {
            return;
          }
          usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(typeArgs[index], typeParameter, true));
        }
      }
    }
  }

  @Override
  public void createCleanupOverriding(@NotNull PsiElement overriddenFunction, PsiElement @NotNull [] elements2Delete,
                                      @NotNull List<? super UsageInfo> result) {
    if (overriddenFunction instanceof PsiMethod method) {
      if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null || method.findSuperMethods().length > 1) return;

      if (JavaSafeDeleteProcessor.canBePrivate(method, elements2Delete)) {
        result.add(new SafeDeletePrivatizeMethod(method, method));
        return;
      }
    }
    result.add(new SafeDeleteOverrideAnnotation(overriddenFunction, overriddenFunction));
  }

  @Override
  public UsageInfo createExtendsListUsageInfo(PsiElement refElement, PsiReference reference) {
    PsiElement element = reference.getElement();
    PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceList && refElement instanceof PsiClass psiClass) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass inheritor && element instanceof PsiJavaCodeReferenceElement classRef) {
        if (parent.equals(inheritor.getPermitsList())) {
          return new SafeDeletePermitsClassUsageInfo(classRef, psiClass, inheritor, true);
        }
        //If psiClass contains only private members, then it is safe to remove it and change inheritor's extends/implements accordingly
        CachedValueProvider<Boolean> provider =
          () -> new CachedValueProvider.Result<>(containsOnlyPrivates(psiClass), PsiModificationTracker.getInstance(psiClass.getProject()));
        if (CachedValuesManager.getCachedValue(psiClass, provider)
            && (parent.equals(inheritor.getExtendsList()) || parent.equals(inheritor.getImplementsList()))) {
          return new SafeDeleteExtendsClassUsageInfo(classRef, psiClass, inheritor);
        }
      }
    }
    return null;
  }

  private static boolean containsOnlyPrivates(final PsiClass aClass) {
    for (PsiField field : aClass.getFields()) {
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    }

    for (PsiMethod method : aClass.getMethods()) {
      if (!method.hasModifierProperty(PsiModifier.PRIVATE)) {
        if (method.isConstructor()) { //skip non-private constructors with call to super only
          final PsiCodeBlock body = method.getBody();
          if (body != null) {
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) continue;
            if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
              final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
              if (JavaPsiConstructorUtil.isSuperConstructorCall(expression)) continue;
            }
          }
        }
        return false;
      }
    }

    for (PsiClass inner : aClass.getInnerClasses()) {
      if (!inner.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    }

    return true;
  }
}

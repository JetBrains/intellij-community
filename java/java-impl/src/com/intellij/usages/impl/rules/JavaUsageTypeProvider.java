// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl.rules;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public final class JavaUsageTypeProvider implements UsageTypeProviderEx {
  @Override
  public UsageType getUsageType(final @NotNull PsiElement element) {
    return getUsageType(element, UsageTarget.EMPTY_ARRAY);
  }

  @Override
  public UsageType getUsageType(PsiElement element, UsageTarget @NotNull [] targets) {
    UsageType classUsageType = getClassUsageType(element, targets);
    if (classUsageType != null) return classUsageType;

    UsageType methodUsageType = getMethodUsageType(element);
    if (methodUsageType != null) return methodUsageType;

    if (element instanceof PsiLiteralExpression) {
      return UsageType.LITERAL_USAGE;
    }

    return null;
  }

  @Nullable
  private static UsageType getMethodUsageType(PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      final PsiMethod containerMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (containerMethod != null) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        final PsiElement p = referenceExpression.getParent();
        if (p instanceof PsiMethodCallExpression callExpression) {
          final PsiMethod calledMethod = callExpression.resolveMethod();
          if (calledMethod == containerMethod) {
            return UsageType.RECURSION;
          }
          if (qualifier != null && !(qualifier instanceof PsiThisExpression) && calledMethod != null) {
            if (Objects.equals(containerMethod.getName(), calledMethod.getName()) &&
                haveCommonSuperMethod(containerMethod, calledMethod)) {
              boolean parametersDelegated = parametersDelegated(containerMethod, callExpression);

              if (qualifier instanceof PsiSuperExpression) {
                return parametersDelegated ? UsageType.DELEGATE_TO_SUPER : UsageType.DELEGATE_TO_SUPER_PARAMETERS_CHANGED;
              }
              else {
                return parametersDelegated ? UsageType.DELEGATE_TO_ANOTHER_INSTANCE : UsageType.DELEGATE_TO_ANOTHER_INSTANCE_PARAMETERS_CHANGED;
              }
            }
          }
        }
      }
    }

    return null;
  }

  private static boolean parametersDelegated(final PsiMethod method, final PsiMethodCallExpression call) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiExpression[] arguments = call.getArgumentList().getExpressions();
    if (parameters.length != arguments.length) return false;

    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiExpression argument = arguments[i];

      if (!(argument instanceof PsiReferenceExpression)) return false;
      if (!((PsiReferenceExpression)argument).isReferenceTo(parameter)) return false;
    }

    for (PsiParameter parameter : parameters) {
      if (HighlightControlFlowUtil.isAssigned(parameter)) return false;
    }

    return true;
  }

  private static boolean haveCommonSuperMethod(@NotNull PsiMethod m1, @NotNull PsiMethod m2) {
    final Queue<PsiMethod> supers1Q = new ArrayDeque<>();
    supers1Q.add(m1);
    final Queue<PsiMethod> supers2Q = new ArrayDeque<>();
    supers2Q.add(m2);
    Set<PsiMethod> supers1 = new HashSet<>();
    Set<PsiMethod> supers2 = new HashSet<>();
    while (true) {
      PsiMethod me1;
      if ((me1 = supers1Q.poll()) != null) {
        if (supers2.contains(me1)) return true;
        supers1.add(me1);
        PsiSuperMethodImplUtil.processDirectSuperMethodsSmart(me1, psiMethod -> {
          supers1Q.add(psiMethod);
          return true;
        });
      }

      PsiMethod me2;
      if ((me2 = supers2Q.poll()) != null) {
        if (supers1.contains(me2)) return true;
        supers2.add(me2);
        PsiSuperMethodImplUtil.processDirectSuperMethodsSmart(me2, psiMethod -> {
          supers2Q.add(psiMethod);
          return true;
        });
      }
      if (me1 == null && me2 == null) break;
    }
    return false;
    /*
    HashSet<PsiMethod> s1 = new HashSet<PsiMethod>(Arrays.asList(m1.findDeepestSuperMethods()));
    s1.add(m1);

    HashSet<PsiMethod> s2 = new HashSet<PsiMethod>(Arrays.asList(m2.findDeepestSuperMethods()));
    s2.add(m2);

    s1.retainAll(s2);
    return !s1.isEmpty();
    */
  }

  @Nullable
  private static UsageType getClassUsageType(@NotNull PsiElement element, UsageTarget @NotNull [] targets) {
    final PsiJavaCodeReferenceElement codeReference = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement.class);
    if(codeReference != null && isNestedClassOf(codeReference,targets)){
      return UsageType.CLASS_NESTED_CLASS_ACCESS;
    }

    if (element.getParent() instanceof PsiAnnotation &&
        element == ((PsiAnnotation)element.getParent()).getNameReferenceElement()) {
      return UsageType.ANNOTATION;
    }

    if (element.getParent() instanceof PsiTypeElement && element.getParent().getParent() instanceof PsiPattern) {
      return UsageType.PATTERN;
    }

    if (PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class, false) != null) return UsageType.CLASS_IMPORT;
    PsiReferenceList referenceList = PsiTreeUtil.getParentOfType(element, PsiReferenceList.class);
    if (referenceList != null) {
      PsiElement parent = referenceList.getParent();
      if (parent instanceof PsiClass aClass) {
        if (aClass.getExtendsList() == referenceList || aClass.getImplementsList() == referenceList) return UsageType.CLASS_EXTENDS_IMPLEMENTS_LIST;
        if (aClass.getPermitsList() == referenceList) return UsageType.CLASS_PERMITS_LIST;
      }
      if (referenceList.getParent() instanceof PsiMethod) return UsageType.CLASS_METHOD_THROWS_LIST;
    }
    if (PsiTreeUtil.getParentOfType(element, PsiTypeParameterList.class) != null ||
        PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class) != null) {
      return UsageType.TYPE_PARAMETER;
    }

    PsiTypeCastExpression castExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
    if (castExpression != null) {
      if (PsiTreeUtil.isAncestor(castExpression.getCastType(), element, true)) return UsageType.CLASS_CAST_TO;
    }

    PsiInstanceOfExpression instanceOfExpression = PsiTreeUtil.getParentOfType(element, PsiInstanceOfExpression.class);
    if (instanceOfExpression != null) {
      if (PsiTreeUtil.isAncestor(instanceOfExpression.getCheckType(), element, true)) return UsageType.CLASS_INSTANCE_OF;
    }

    if (PsiTreeUtil.getParentOfType(element, PsiClassObjectAccessExpression.class) != null) return UsageType.CLASS_CLASS_OBJECT_ACCESS;

    final PsiMethodReferenceExpression methodReferenceExpression = PsiTreeUtil.getParentOfType(element, PsiMethodReferenceExpression.class);
    if (methodReferenceExpression != null && methodReferenceExpression.isConstructor()) {
      final PsiElement qualifier = methodReferenceExpression.getQualifier();
      if (qualifier instanceof PsiTypeElement && ((PsiTypeElement)qualifier).getType() instanceof PsiArrayType) {
        return UsageType.CLASS_NEW_ARRAY;
      }
      return UsageType.CLASS_NEW_OPERATOR;
    }

    if (element instanceof PsiReferenceExpression expression && expression.resolve() instanceof PsiClass) {
      return UsageType.CLASS_STATIC_MEMBER_ACCESS;
    }

    final PsiParameter psiParameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (psiParameter != null) {
      final PsiElement scope = psiParameter.getDeclarationScope();
      if (scope instanceof PsiMethod) return UsageType.CLASS_METHOD_PARAMETER_DECLARATION;
      if (scope instanceof PsiCatchSection) return UsageType.CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION;
      if (scope instanceof PsiForeachStatement) return UsageType.CLASS_LOCAL_VAR_DECLARATION;
      return null;
    }

    PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (psiField != null) {
      if (PsiTreeUtil.isAncestor(psiField.getTypeElement(), element, true)) return UsageType.CLASS_FIELD_DECLARATION;
    }

    PsiLocalVariable psiLocalVar = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (psiLocalVar != null) {
      if (PsiTreeUtil.isAncestor(psiLocalVar.getTypeElement(), element, true)) return UsageType.CLASS_LOCAL_VAR_DECLARATION;
    }

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (psiMethod != null) {
      final PsiTypeElement retType = psiMethod.getReturnTypeElement();
      if (retType != null && PsiTreeUtil.isAncestor(retType, element, true)) return UsageType.CLASS_METHOD_RETURN_TYPE;
    }

    final PsiNewExpression psiNewExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
    if (psiNewExpression != null) {
      final PsiJavaCodeReferenceElement classReference = psiNewExpression.getClassOrAnonymousClassReference();
      if (classReference != null && PsiTreeUtil.isAncestor(classReference, element, false)) {
        if (isAnonymousClassOf(psiNewExpression.getAnonymousClass(), targets)) {
          return UsageType.CLASS_ANONYMOUS_NEW_OPERATOR;
        }
        if (psiNewExpression.isArrayCreation()) {
          return UsageType.CLASS_NEW_ARRAY;
        }
        return UsageType.CLASS_NEW_OPERATOR;
      }
    }

    return null;
  }

  private static boolean isAnonymousClassOf(@Nullable PsiAnonymousClass anonymousClass, UsageTarget @NotNull [] targets) {
    if (anonymousClass == null) {
      return false;
    }

    return qualifiesToTargetClasses(anonymousClass.getBaseClassReference(), targets);
  }

  private static boolean isNestedClassOf(PsiJavaCodeReferenceElement classReference, UsageTarget @NotNull [] targets) {
    if (classReference instanceof PsiMethodReferenceExpression) return false;
    final PsiElement qualifier = classReference.getQualifier();
    if (qualifier instanceof PsiJavaCodeReferenceElement) {
      return qualifiesToTargetClasses((PsiJavaCodeReferenceElement)qualifier, targets) && classReference.resolve() instanceof PsiClass;
    }
    return false;
  }

  private static boolean qualifiesToTargetClasses(@NotNull PsiJavaCodeReferenceElement qualifier, UsageTarget @NotNull [] targets) {
    for (UsageTarget target : targets) {
      if (target instanceof PsiElementUsageTarget) {
        PsiElement element = ((PsiElementUsageTarget)target).getElement();
        if (element instanceof PsiClass) {
          if (Objects.equals(qualifier.getReferenceName(), target.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }
}

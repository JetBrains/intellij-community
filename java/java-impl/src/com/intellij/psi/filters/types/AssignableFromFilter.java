// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.types;

import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.Nullable;


public class AssignableFromFilter implements ElementFilter {
  private PsiType myType;
  private String myClassName;

  public AssignableFromFilter(PsiType type) {
    myType = type;
  }

  public AssignableFromFilter(final String className) {
    myClassName = className;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    PsiType type = myType;
    if (type == null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(context.getProject());
      final PsiClass aClass = psiFacade.findClass(myClassName, context.getResolveScope());
      if (aClass != null) {
        type = psiFacade.getElementFactory().createType(aClass, PsiSubstitutor.EMPTY);
      }
    }
    if (type == null) return false;
    if (element == null) return false;
    if (element instanceof PsiType) return type.isAssignableFrom((PsiType)element);

    PsiSubstitutor substitutor = null;

    if (element instanceof CandidateInfo info) {
      substitutor = info.getSubstitutor();
      element = info.getElement();
    }

    return isAcceptable((PsiElement)element, context, type, substitutor);
  }

  public static boolean isAcceptable(PsiElement element, PsiElement context, PsiType expectedType, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod && isReturnTypeInferrable((PsiMethod)element, context, expectedType, substitutor)) {
      return true;
    }

    PsiType typeByElement = FilterUtil.getTypeByElement(element, context);
    if (typeByElement == null) {
      return false;
    }

    if (substitutor != null) {
      typeByElement = substitutor.substitute(typeByElement);
    }

    if (!allowBoxing(context) && (expectedType instanceof PsiPrimitiveType != typeByElement instanceof PsiPrimitiveType)) {
      return false;
    }

    try {
      return expectedType.isAssignableFrom(typeByElement);
    }
    catch (Throwable e) {
      if (ExceptionUtil.getRootCause(e) instanceof PsiInvalidElementAccessException) {
        PsiUtil.ensureValidType(expectedType);
        throw new RuntimeException("Invalid type of " + element.getClass(), e);
      }
      throw e;
    }
  }

  private static boolean allowBoxing(PsiElement place) {
    final PsiElement parent = place.getParent();
    if (parent.getParent() instanceof PsiSynchronizedStatement statement && parent.equals(statement.getLockExpression())) {
      return false;
    }
    return true;
  }

  private static boolean isReturnTypeInferrable(PsiMethod method,
                                                PsiElement place,
                                                PsiType expectedType,
                                                @Nullable PsiSubstitutor substitutor) {
    final PsiResolveHelper helper = JavaPsiFacade.getInstance(method.getProject()).getResolveHelper();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();
    if (typeParameters.length == 0) return false;
    PsiType returnType = method.getReturnType();
    if (substitutor != null) {
      returnType = substitutor.substitute(returnType);
    }
    if (returnType instanceof PsiClassType) {
      PsiClass target = ((PsiClassType)returnType).resolve();
      if (!(target instanceof PsiTypeParameter) && !expectedType.isAssignableFrom(((PsiClassType)returnType).rawType())) {
        return false;
      }
    }
    for (final PsiTypeParameter parameter : typeParameters) {
      final PsiType substitutionForParameter = helper.getSubstitutionForTypeParameter(parameter,
                                                                                      returnType,
                                                                                      expectedType,
                                                                                      false,
                                                                                      PsiUtil.getLanguageLevel(place));
      if (substitutionForParameter != PsiTypes.nullType() &&
          !isImpossibleIntersection(substitutionForParameter) &&
          !extendsImpossibleIntersection(PsiUtil.resolveClassInClassTypeOnly(substitutionForParameter)) &&
          PsiUtil.resolveClassInClassTypeOnly(substitutionForParameter) != parameter) {
        return true;
      }
    }
    return false;
  }

  private static boolean extendsImpossibleIntersection(@Nullable PsiClass psiClass) {
    if (psiClass instanceof PsiTypeParameter) {
      PsiClassType[] supers = psiClass.getExtendsListTypes();
      if (supers.length > 1) {
        return isImpossibleIntersection(PsiIntersectionType.createIntersection(supers));
      }
    }
    return false;
  }

  private static boolean isImpossibleIntersection(PsiType intersection) {
    return intersection instanceof PsiIntersectionType && ((PsiIntersectionType)intersection).getConflictingConjunctsMessage() != null;
  }

  @Override
  public String toString() {
    return "assignable-from(" + (myType != null ? myType : myClassName) + ")";
  }
}

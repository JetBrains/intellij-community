/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author peter
 */
public class JavaMethodResolveHelper {
  private final Map<MethodSignature, PsiMethod> myMethods = new LinkedHashMap<MethodSignature, PsiMethod>();
  @Nullable private final PsiType[] myParameterTypes;
  private MyApplicability myApplicability;

  enum MyApplicability {
    NotApplicable,
    ParamCount,
    Static,
    Applicable,
  }

  public JavaMethodResolveHelper(@Nullable final PsiType[] parameterTypes) {
    myParameterTypes = parameterTypes;
  }

  public void addMethod(PsiMethod method, PsiSubstitutor substitutor, boolean staticError) {
    final MethodSignature signature = method.getSignature(substitutor);
    final PsiMethod psiMethod = myMethods.get(signature);

    if (psiMethod != null) {
      for (final MethodSignatureBackedByPsiMethod methodSignature : SuperMethodsSearch
          .search(psiMethod, psiMethod.getContainingClass(), true, false).findAll()) {
        if (methodSignature.equals(signature)) return;
      }
    }

    if (myParameterTypes != null) {
      if (myApplicability == null) myApplicability = MyApplicability.NotApplicable;
      MyApplicability applicability = allowStaticErrors(getApplicability(method, myParameterTypes), staticError);

      if (applicability.compareTo(myApplicability) > 0) {
        myMethods.clear();
        myMethods.put(signature, method);
        myApplicability = applicability;
      } else if (applicability == myApplicability) {
        myMethods.put(signature, method);
      }
    } else {
      if (myApplicability == null) myApplicability = MyApplicability.Applicable;
      myApplicability = allowStaticErrors(myApplicability, staticError);
      myMethods.put(signature, method);
    }
  }

  private static MyApplicability allowStaticErrors(MyApplicability applicability, final boolean staticError) {
    if (applicability == MyApplicability.Applicable && staticError) {
      applicability = MyApplicability.Static;
    }
    return applicability;
  }

  private static MyApplicability getApplicability(final PsiMethod method, @NotNull final PsiType[] parameterTypes) {
    if (method.getParameterList().getParametersCount() == parameterTypes.length) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < parameterTypes.length; i++) {
        PsiType type = parameterTypes[i];
        if (type == null) return MyApplicability.ParamCount;
        if (!parameters[i].getType().isAssignableFrom(type)) return MyApplicability.NotApplicable;
      }
      return MyApplicability.Applicable;
    }
    return MyApplicability.NotApplicable;
  }

  @NotNull
  public ErrorType getResolveError() {
    if (myApplicability == MyApplicability.Static) return ErrorType.STATIC;
    if (myApplicability == MyApplicability.NotApplicable || myApplicability == MyApplicability.ParamCount && myMethods.size() > 1) return ErrorType.RESOLVE;
    return ErrorType.NONE;
  }

  public static enum ErrorType {
    NONE, STATIC, RESOLVE
  }

  public Collection<PsiMethod> getMethods() {
    return myMethods.values();
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.MethodImplementor;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SnippetDocTagMethodImplementor implements MethodImplementor {
  @Override
  public PsiMethod @NotNull [] getMethodsToImplement(PsiClass aClass) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod @NotNull [] createImplementationPrototypes(PsiClass inClass,
                                                              PsiMethod method) throws IncorrectOperationException {
    final PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(inClass.getProject()).getInjectionHost(inClass);
    if (!(injectionHost instanceof PsiSnippetDocTag)) {
      return PsiMethod.EMPTY_ARRAY;
    }

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return PsiMethod.EMPTY_ARRAY;
    }

    final PsiSubstitutor substitutor = inClass.isInheritor(containingClass, true)
                                       ? TypeConversionUtil.getSuperClassSubstitutor(containingClass, inClass, PsiSubstitutor.EMPTY)
                                       : PsiSubstitutor.EMPTY;
    final PsiMethod result = GenerateMembersUtil.substituteGenericMethod(method, substitutor, inClass);

    return new PsiMethod[]{result};
  }

  @Override
  public @Nullable GenerationInfo createGenerationInfo(PsiMethod method,
                                                       boolean mergeIfExists) {
    return null;
  }

  @Override
  public @NotNull Consumer<PsiMethod> createDecorator(PsiClass targetClass,
                                                      final PsiMethod baseMethod,
                                                      boolean toCopyJavaDoc,
                                                      boolean insertOverrideIfPossible) {
    return result -> OverrideImplementUtil.decorateMethod(targetClass, baseMethod, false, insertOverrideIfPossible, result);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.compiler.CompilerReferenceService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiMethod;

final class PreferMostUsedWeigher extends LookupElementWeigher {
  private static final PsiMethodPattern OBJECT_METHOD_PATTERN = psiMethod().withName(
    StandardPatterns.string().oneOf("hashCode", "equals", "finalize", "wait", "notify", "notifyAll", "getClass", "clone", "toString")).
    inClass(CommonClassNames.JAVA_LANG_OBJECT);
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication().isUnitTestMode();

  private final CompilerReferenceService myCompilerReferenceService;
  private final boolean myConstructorSuggestion;

  private PreferMostUsedWeigher(@NotNull CompilerReferenceService service, boolean constructorSuggestion) {
    super("mostUsed");
    myCompilerReferenceService = service;
    myConstructorSuggestion = constructorSuggestion;
  }

  // optimization: do not even create weigher if compiler indices aren't available for now
  @Nullable
  static PreferMostUsedWeigher create(@NotNull PsiElement position) {
    final CompilerReferenceService service = CompilerReferenceService.getInstance(position.getProject());
    if (service == null) return null;
    return service.isActive() || UNIT_TEST_MODE ? new PreferMostUsedWeigher(service, JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) : null;
  }

  @Nullable
  @Override
  public Integer weigh(@NotNull LookupElement element) {
    final PsiElement psi = ObjectUtils.tryCast(element.getObject(), PsiElement.class);
    if (!(psi instanceof PsiMember)) {
      return null;
    }
    if (element.getUserData(JavaGenerateMemberCompletionContributor.GENERATE_ELEMENT) != null) {
      return null;
    }
    if (OBJECT_METHOD_PATTERN.accepts(psi)) {
      return null;
    }
    if (looksLikeHelperMethodOrConst(psi)) {
      return null;
    }
    final Integer occurrenceCount = myCompilerReferenceService.getCompileTimeOccurrenceCount(psi, myConstructorSuggestion);
    return occurrenceCount == null ? null : -occurrenceCount;
  }

  //Objects.requireNonNull is an example
  private static boolean looksLikeHelperMethodOrConst(@NotNull PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)element;
    if (method.isConstructor()) return false;
    if (isRawDeepTypeEqualToObject(method.getReturnType())) return true;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) return false;
    for (PsiParameter parameter : parameters) {
      PsiType paramType = parameter.getType();
      if (isRawDeepTypeEqualToObject(paramType)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isRawDeepTypeEqualToObject(@Nullable PsiType type) {
    if (type == null) return false;
    PsiType rawType = TypeConversionUtil.erasure(type.getDeepComponentType());
    if (rawType == null) return false;
    return rawType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
  }
}

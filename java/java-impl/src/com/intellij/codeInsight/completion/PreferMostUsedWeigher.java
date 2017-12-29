/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

class PreferMostUsedWeigher extends LookupElementWeigher {
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

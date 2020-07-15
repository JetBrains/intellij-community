// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.JavaSmartCompletionParameters;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClassLiteralGetter {

  public static void addCompletions(@NotNull final JavaSmartCompletionParameters parameters,
                                    @NotNull Consumer<? super LookupElement> result, final PrefixMatcher matcher) {
    PsiType expectedType = parameters.getDefaultType();
    if (!InheritanceUtil.isInheritor(expectedType, CommonClassNames.JAVA_LANG_CLASS)) {
      expectedType = parameters.getExpectedType();
      if (!InheritanceUtil.isInheritor(expectedType, CommonClassNames.JAVA_LANG_CLASS)) {
        return;
      }
    }

    PsiType classParameter = PsiUtil.substituteTypeParameter(expectedType, CommonClassNames.JAVA_LANG_CLASS, 0, false);

    boolean addInheritors = false;
    PsiElement position = parameters.getPosition();
    if (classParameter instanceof PsiWildcardType) {
      final PsiWildcardType wildcardType = (PsiWildcardType)classParameter;
      classParameter = wildcardType.isSuper() ? wildcardType.getSuperBound() : wildcardType.getExtendsBound();
      addInheritors = wildcardType.isExtends() && classParameter instanceof PsiClassType;
    } else if (!matcher.getPrefix().isEmpty()) {
      addInheritors = true;
      classParameter = PsiType.getJavaLangObject(position.getManager(), position.getResolveScope());
    }
    if (classParameter != null) {
      PsiFile file = position.getContainingFile();
      addClassLiteralLookupElement(classParameter, result, file);
      if (addInheritors) {
        addInheritorClassLiterals(file, classParameter, result, matcher);
      }
    }
  }

  private static void addInheritorClassLiterals(final PsiFile context,
                                                final PsiType classParameter,
                                                final Consumer<? super LookupElement> result, PrefixMatcher matcher) {
    final String canonicalText = classParameter.getCanonicalText();
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(canonicalText) && StringUtil.isEmpty(matcher.getPrefix())) {
      return;
    }

    CodeInsightUtil.processSubTypes(classParameter, context, true, matcher, type -> addClassLiteralLookupElement(type, result, context));
  }

  private static void addClassLiteralLookupElement(@Nullable final PsiType type, final Consumer<? super LookupElement> resultSet, final PsiFile context) {
    if (type instanceof PsiClassType &&
        PsiUtil.resolveClassInType(type) != null &&
        !((PsiClassType)type).hasParameters() &&
        !(((PsiClassType)type).resolve() instanceof PsiTypeParameter)) {
      try {
        resultSet.consume(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new ClassLiteralLookupElement((PsiClassType)type, context)));
      }
      catch (IncorrectOperationException ignored) {
      }
    }
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.JavaSmartCompletionParameters;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassLiteralGetter {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.filters.getters.ClassLiteralGetter");

  public static void addCompletions(@NotNull final JavaSmartCompletionParameters parameters,
                                    @NotNull Consumer<LookupElement> result, final PrefixMatcher matcher) {

    final Condition<String> shortNameCondition = new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };

    PsiType classParameter = PsiUtil.substituteTypeParameter(parameters.getExpectedType(), CommonClassNames.JAVA_LANG_CLASS, 0, false);

    boolean addInheritors = false;
    PsiElement position = parameters.getPosition();
    if (classParameter instanceof PsiWildcardType) {
      final PsiWildcardType wildcardType = (PsiWildcardType)classParameter;
      classParameter = wildcardType.getBound();
      addInheritors = wildcardType.isExtends() && classParameter instanceof PsiClassType;
    } else if (!matcher.getPrefix().isEmpty()) {
      addInheritors = true;
      classParameter = PsiType.getJavaLangObject(position.getManager(), position.getResolveScope());
    }
    if (classParameter != null) {
      PsiFile file = position.getContainingFile();
      addClassLiteralLookupElement(classParameter, result, file);
      if (addInheritors) {
        addInheritorClassLiterals(file, shortNameCondition, classParameter, result, matcher);
      }
    }
  }

  private static void addInheritorClassLiterals(PsiFile context,
                                                Condition<String> shortNameCondition,
                                                final PsiType classParameter,
                                                Consumer<LookupElement> result, PrefixMatcher matcher) {
    final String canonicalText = classParameter.getCanonicalText();
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(canonicalText) && StringUtil.isEmpty(matcher.getPrefix())) {
      return;
    }

    for (final PsiType type : CodeInsightUtil.addSubtypes(classParameter, context, true, shortNameCondition)) {
      addClassLiteralLookupElement(type, result, context);
    }
  }

  private static void addClassLiteralLookupElement(@Nullable final PsiType type, final Consumer<LookupElement> resultSet, final PsiFile context) {
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

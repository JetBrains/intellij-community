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
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaSmartCompletionParameters;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClassLiteralGetter extends CompletionProvider<JavaSmartCompletionParameters> {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.filters.getters.ClassLiteralGetter");

  public ClassLiteralGetter() {
    super(false);
  }

  @Override
  protected void addCompletions(@NotNull final JavaSmartCompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {

    final PrefixMatcher matcher = result.getPrefixMatcher();
    final Condition<String> shortNameCondition = new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };

    PsiType classParameter = ApplicationManager.getApplication().runReadAction(new Computable<PsiType>() {
      @Nullable
      public PsiType compute() {
        return PsiUtil.substituteTypeParameter(parameters.getExpectedType(), CommonClassNames.JAVA_LANG_CLASS, 0, false);
      }
    });

    boolean addInheritors = false;
    if (classParameter instanceof PsiWildcardType) {
      final PsiWildcardType wildcardType = (PsiWildcardType)classParameter;
      classParameter = wildcardType.getBound();
      addInheritors = wildcardType.isExtends() && classParameter instanceof PsiClassType;
    }
    if (classParameter == null) {
      return;
    }

    addClassLiteralLookupElement(classParameter, result, parameters.getPosition());
    if (addInheritors) {
      addInheritorClassLiterals(parameters.getPosition(), shortNameCondition, classParameter, result);
    }

  }

  private static void addInheritorClassLiterals(PsiElement context,
                                                Condition<String> shortNameCondition,
                                                final PsiType classParameter,
                                                CompletionResultSet result) {
    final String canonicalText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return classParameter.getCanonicalText();
      }
    });
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(canonicalText) && StringUtil.isEmpty(result.getPrefixMatcher().getPrefix())) {
      return;
    }

    for (final PsiType type : CodeInsightUtil.addSubtypes(classParameter, context, true, shortNameCondition)) {
      addClassLiteralLookupElement(type, result, context);
    }
  }

  private static void addClassLiteralLookupElement(@Nullable final PsiType type, final CompletionResultSet resultSet, final PsiElement context) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (type instanceof PsiClassType &&
            type.isValid() &&
            PsiUtil.resolveClassInType(type) != null &&
            !((PsiClassType)type).hasParameters() &&
            !(((PsiClassType)type).resolve() instanceof PsiTypeParameter)) {
          try {
            resultSet.addElement(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(new ClassLiteralLookupElement((PsiClassType)type, context)));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    });

  }
}

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
package com.intellij.refactoring.rename;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaNameSuggestionProvider implements NameSuggestionProvider {
  @Nullable
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, final PsiElement nameSuggestionContext, List<String> result) {
    String initialName = UsageViewUtil.getShortName(element);
    SuggestedNameInfo info = suggestNamesForElement(element);
    if (info != null) {
      info = JavaCodeStyleManager.getInstance(element.getProject()).suggestUniqueVariableName(info, element, true);
    }

    String parameterName = null;
    String superMethodName = null;
    if (nameSuggestionContext != null) {
      final PsiElement nameSuggestionContextParent = nameSuggestionContext.getParent();
      if (nameSuggestionContextParent != null) {
        final PsiElement parentOfParent = nameSuggestionContextParent.getParent();
        if (parentOfParent instanceof PsiExpressionList) {
          final PsiExpressionList expressionList = (PsiExpressionList)parentOfParent;
          final PsiElement parent = expressionList.getParent();
          if (parent instanceof PsiCallExpression) {
            final PsiMethod method = ((PsiCallExpression)parent).resolveMethod();
            if (method != null) {
              final PsiParameter[] parameters = method.getParameterList().getParameters();
              final PsiExpression[] expressions = expressionList.getExpressions();
              for (int i = 0; i < expressions.length; i++) {
                PsiExpression expression = expressions[i];
                if (expression == nameSuggestionContextParent) {
                  if (i < parameters.length) {
                    parameterName = parameters[i].getName();
                  }
                  break;
                }
              }
            }
          }
        }
        else if (parentOfParent instanceof PsiParameterList) {
          final PsiElement parent3 = parentOfParent.getParent();
          if (parent3 instanceof PsiMethod) {
            final String propName = PropertyUtil.getPropertyName((PsiMethod)parent3);
            if (propName != null) {
              parameterName = propName;
            }
            if (nameSuggestionContextParent instanceof PsiParameter) {
              superMethodName = getSuperMethodName((PsiParameter) nameSuggestionContextParent, (PsiMethod) parent3);
            }
          }
        }
      }
    }
    final String[] strings = info != null ? info.names : ArrayUtil.EMPTY_STRING_ARRAY;
    ArrayList<String> list = new ArrayList<String>(Arrays.asList(strings));
    final String[] properlyCased = suggestProperlyCasedName(element);
    if (!list.contains(initialName)) {
      list.add(0, initialName);
    }
    else {
      int i = list.indexOf(initialName);
      list.remove(i);
      list.add(0, initialName);
    }
    if (properlyCased != null) {
      for (String properlyCasedSuggestion : properlyCased) {
        list.add(1, properlyCasedSuggestion);
      }
    }
    if (parameterName != null && !list.contains(parameterName)) {
      list.add(parameterName);
    }
    if (superMethodName != null && !list.contains(superMethodName)) {
      list.add(0, superMethodName);
    }
    ContainerUtil.removeDuplicates(list);
    result.addAll(list);
    return info;
  }

  @Nullable
  private static String getSuperMethodName(final PsiParameter psiParameter, final PsiMethod method) {
    final int index = method.getParameterList().getParameterIndex(psiParameter);
    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      final PsiParameterList superParameters = superMethod.getParameterList();
      if (index < superParameters.getParametersCount()) {
        return superParameters.getParameters() [index].getName();
      }
    }
    return null;
  }

  @Nullable
  public Collection<LookupElement> completeName(final PsiElement element, final PsiElement nameSuggestionContext, final String prefix) {
    if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)element;
      VariableKind kind = JavaCodeStyleManager.getInstance(element.getProject()).getVariableKind(var);
      Set<LookupElement> set = new LinkedHashSet<LookupElement>();
      JavaCompletionUtil.completeVariableNameForRefactoring(element.getProject(), set, prefix, var.getType(), kind);

      if (prefix.length() == 0) {
        List<String> suggestedNames = new ArrayList<String>();
        getSuggestedNames(element, nameSuggestionContext, suggestedNames);
        for (String suggestedName : suggestedNames) {
          LookupItemUtil.addLookupItem(set, suggestedName);
        }
      }

    }
    return null;
  }

  @Nullable
  private static String[] suggestProperlyCasedName(PsiElement psiElement) {
    if (!(psiElement instanceof PsiNamedElement)) return null;
    String name = ((PsiNamedElement)psiElement).getName();
    if (name == null) return null;
    if (psiElement instanceof PsiVariable) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiElement.getProject());
      final VariableKind kind = codeStyleManager.getVariableKind((PsiVariable)psiElement);
      final String prefix = codeStyleManager.getPrefixByVariableKind(kind);
      if (kind == VariableKind.STATIC_FINAL_FIELD) {
        final String[] words = NameUtil.splitNameIntoWords(name);
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
          String word = words[i];
          if (i > 0) buffer.append('_');
          buffer.append(word.toUpperCase());
        }
        return new String[] {buffer.toString()};
      }
      else {
        final List<String> result = new ArrayList<String>();
        result.add(suggestProperlyCasedName(prefix, NameUtil.splitNameIntoWords(name)));
        if (name.startsWith(prefix)) {
          name = name.substring(prefix.length());
          result.add(suggestProperlyCasedName(prefix, NameUtil.splitNameIntoWords(name)));
        }
        result.add(suggestProperlyCasedName(prefix, NameUtil.splitNameIntoWords(name.toLowerCase())));
        return ArrayUtil.toStringArray(result);
      }

    }
    return new String[]{name};
  }

  private static String suggestProperlyCasedName(String prefix, String[] words) {
    StringBuilder buffer = new StringBuilder(prefix);
    for (int i = 0; i < words.length; i++) {
      String word = words[i];
      final boolean prefixRequiresCapitalization = prefix.length() > 0 && !StringUtil.endsWithChar(prefix, '_');
      if (i > 0 || prefixRequiresCapitalization) {
        buffer.append(StringUtil.capitalize(word));
      }
      else {
        buffer.append(StringUtil.decapitalize(word));
      }
    }
    return buffer.toString();
  }

  @Nullable
  private static SuggestedNameInfo suggestNamesForElement(final PsiElement element) {
    PsiVariable var = null;
    if (element instanceof PsiVariable) {
      var = (PsiVariable)element;
    }
    else if (element instanceof PsiIdentifier) {
      PsiIdentifier identifier = (PsiIdentifier)element;
      if (identifier.getParent() instanceof PsiVariable) {
        var = (PsiVariable)identifier.getParent();
      }
    }

    if (var == null) return null;

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(element.getProject());
    VariableKind variableKind = codeStyleManager.getVariableKind(var);
    return codeStyleManager.suggestVariableName(variableKind, null, var.getInitializer(), var.getType());
  }

}

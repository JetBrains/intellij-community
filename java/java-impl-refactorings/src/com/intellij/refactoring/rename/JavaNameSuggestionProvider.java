// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class JavaNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  @Nullable
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, final PsiElement nameSuggestionContext, Set<String> result) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return null;
    String initialName = UsageViewUtil.getShortName(element);
    SuggestedNameInfo info = suggestNamesForElement(element, nameSuggestionContext);
    if (info != null) {
      info = JavaCodeStyleManager.getInstance(element.getProject()).suggestUniqueVariableName(info, element, true, true);
    }

    String parameterName = null;
    String superMethodName = null;
    if (nameSuggestionContext instanceof PsiParameter) {
      final PsiElement nameSuggestionContextParent = nameSuggestionContext.getParent();
      if (nameSuggestionContextParent instanceof PsiParameterList) {
        final PsiElement parentOfParent = nameSuggestionContextParent.getParent();
        if (parentOfParent instanceof PsiMethod) {
          final String propName = PropertyUtilBase.getPropertyName((PsiMethod)parentOfParent);
          if (propName != null) {
            parameterName = propName;
          }
          superMethodName = getSuperMethodName((PsiParameter) nameSuggestionContext, (PsiMethod) parentOfParent);
        }
      }
    }
    final String[] strings = info != null ? info.names : ArrayUtilRt.EMPTY_STRING_ARRAY;
    final ArrayList<String> list = new ArrayList<>(Arrays.asList(strings));
    final String[] properlyCased = suggestProperlyCasedName(element);
    if (properlyCased != null) {
      Collections.addAll(list, properlyCased);
    }
    if (parameterName != null && !list.contains(parameterName)) {
      list.add(parameterName);
    }
    if (superMethodName != null && !list.contains(superMethodName)) {
      list.add(0, superMethodName);
    }
    list.remove(initialName);
    list.add(initialName);
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

  private static String @Nullable [] suggestProperlyCasedName(PsiElement psiElement) {
    if (!(psiElement instanceof PsiNamedElement)) return null;
    if (psiElement instanceof PsiFile) return null;
    String name = ((PsiNamedElement)psiElement).getName();
    if (name == null) return null;
    String prefix = "";
    if (psiElement instanceof PsiVariable) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiElement.getProject());
      final VariableKind kind = codeStyleManager.getVariableKind((PsiVariable)psiElement);
      prefix = codeStyleManager.getPrefixByVariableKind(kind);
      if (kind == VariableKind.STATIC_FINAL_FIELD) {
        final String[] words = NameUtilCore.splitNameIntoWords(name);
        String buffer = Arrays.stream(words).map(StringUtil::toUpperCase).collect(Collectors.joining("_"));
        return new String[] {buffer};
      }
    }
    final List<String> result = new ArrayList<>();
    result.add(suggestProperlyCasedName(prefix, NameUtilCore.splitNameIntoWords(name)));
    if (name.startsWith(prefix) && !prefix.isEmpty()) {
      name = name.substring(prefix.length());
      result.add(suggestProperlyCasedName(prefix, NameUtilCore.splitNameIntoWords(name)));
    }
    result.add(suggestProperlyCasedName(prefix, NameUtilCore.splitNameIntoWords(StringUtil.toLowerCase(name))));
    return ArrayUtilRt.toStringArray(result);
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
  private static SuggestedNameInfo suggestNamesForElement(final PsiElement element, PsiElement nameSuggestionContext) {
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
    final SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(variableKind, null, var.getInitializer(), var.getType());
    final PsiExpression expression = PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiCallExpression.class, false, PsiLambdaExpression.class, PsiClass.class);
    if (expression != null) {
      return new SuggestedNameInfo.Delegate(codeStyleManager.suggestVariableName(variableKind, null, expression, var.getType()).names, nameInfo);

    }
    return nameInfo;
  }

}

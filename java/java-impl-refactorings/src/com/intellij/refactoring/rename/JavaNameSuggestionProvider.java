// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class JavaNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  public @Nullable SuggestedNameInfo getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, Set<String> result) {
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return null;
    String initialName = UsageViewUtil.getShortName(element);
    SuggestedNameInfo info = suggestNamesForElement(element, nameSuggestionContext);
    if (info != null) {
      info = JavaCodeStyleManager.getInstance(element.getProject()).suggestUniqueVariableName(info, element, true, true);
    }

    String parameterName = null;
    String superMethodName = null;
    if (nameSuggestionContext instanceof PsiParameter parameter) {
      final PsiElement nameSuggestionContextParent = nameSuggestionContext.getParent();
      if (nameSuggestionContextParent instanceof PsiParameterList) {
        final PsiElement parentOfParent = nameSuggestionContextParent.getParent();
        if (parentOfParent instanceof PsiMethod method) {
          final String propName = PropertyUtilBase.getPropertyName(method);
          if (propName != null) {
            parameterName = propName;
          }
          superMethodName = getSuperMethodName(parameter, method);
        }
      }
    }
    final List<String> list = suggestProperlyCasedNames(element);
    final String[] strings = info != null ? info.names : ArrayUtilRt.EMPTY_STRING_ARRAY;
    ContainerUtil.addAll(list, strings);
    if (parameterName != null && !list.contains(parameterName)) {
      list.add(parameterName);
    }
    if (superMethodName != null && !list.contains(superMethodName)) {
      list.addFirst(superMethodName);
    }
    ContainerUtil.removeDuplicates(list);
    list.remove(initialName);
    result.addAll(list);
    return info;
  }

  private static @Nullable String getSuperMethodName(PsiParameter psiParameter, PsiMethod method) {
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

  private static List<String> suggestProperlyCasedNames(PsiElement element) {
    final List<String> result = new ArrayList<>();
    if (element instanceof PsiFile || !(element instanceof PsiNamedElement named)) return result;
    String name = named.getName();
    if (name == null) return result;
    String prefix = "";
    boolean capitalize = element instanceof PsiClass;
    if (element instanceof PsiVariable var) {
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(element.getProject());
      final VariableKind kind = codeStyleManager.getVariableKind(var);
      prefix = codeStyleManager.getPrefixByVariableKind(kind);
      if (kind == VariableKind.STATIC_FINAL_FIELD) {
        final List<@NotNull String> words = NameUtilCore.splitNameIntoWordList(name);
        do {
          result.add(words.stream().map(StringUtil::toUpperCase).collect(Collectors.joining("_")));
          words.removeFirst();
        } while (!words.isEmpty());
        return result;
      }
      else if (isUpperCase(name)) {
        suggestProperlyCasedNames(prefix, NameUtilCore.splitNameIntoWordList(StringUtil.toLowerCase(name)), capitalize, result);
      }
    }
    if (!isUpperCase(name)) {
      suggestProperlyCasedNames(prefix, NameUtilCore.splitNameIntoWordList(name), capitalize, result);
    }
    if (name.startsWith(prefix) && !prefix.isEmpty()) {
      name = name.substring(prefix.length());
      suggestProperlyCasedNames(prefix, NameUtilCore.splitNameIntoWordList(name), capitalize, result);
    }
    return result;
  }
  
  private static boolean isUpperCase(String s) {
    for (int i = 0, length = s.length(); i < length; i++) {
      char c = s.charAt(i);
      if (!Character.isUpperCase(c) && Character.isLetter(c)) return false;
    }
    return true;
  }

  private static void suggestProperlyCasedNames(String prefix, List<@NotNull String> words, boolean capitalize, List<String> result) {
    StringBuilder buffer = new StringBuilder(prefix);
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      boolean requiresCapitalization = capitalize || i > 0 || !prefix.isEmpty() && !StringUtil.endsWithChar(prefix, '_');
      buffer.append(requiresCapitalization ? StringUtil.capitalize(word) : StringUtil.decapitalize(word));
    }
    if (!buffer.isEmpty() && Character.isJavaIdentifierStart(buffer.charAt(0))) {
      result.add(buffer.toString());
    }
    if (words.size() > 1) {
      words.removeFirst();
      suggestProperlyCasedNames(prefix, words, capitalize, result);
    }
  }

  private static @Nullable SuggestedNameInfo suggestNamesForElement(PsiElement element, PsiElement nameSuggestionContext) {
    PsiVariable variable = null;
    if (element instanceof PsiVariable var) {
      variable = var;
    }
    else if (element instanceof PsiIdentifier identifier && identifier.getParent() instanceof PsiVariable parent) {
      variable = parent;
    }

    if (variable == null) return null;

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(element.getProject());
    VariableKind variableKind = codeStyleManager.getVariableKind(variable);
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(variableKind, null, variable.getInitializer(), variable.getType());
    PsiExpression expression =
      PsiTreeUtil.getParentOfType(nameSuggestionContext, PsiCallExpression.class, false, PsiLambdaExpression.class, PsiClass.class);
    if (expression == null) {
      return nameInfo;
    }
    String[] names = codeStyleManager.suggestVariableName(variableKind, null, expression, variable.getType()).names;
    return new SuggestedNameInfo.Delegate(names, nameInfo);
  }
}

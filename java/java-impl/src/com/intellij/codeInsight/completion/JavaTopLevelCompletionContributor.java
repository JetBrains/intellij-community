// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaTopLevelCompletionContributor implements TopLevelCompletionContributor {
  @Override
  public void addLookupElements(@NotNull PsiFile file, int invocationCount, @NotNull CompletionResultSet result) {
    PsiJavaFile javaFile = ObjectUtils.tryCast(file, PsiJavaFile.class);
    if (javaFile == null) return;
    PrefixMatcher currentMatcher = result.getPrefixMatcher();
    for (PsiClass aClass : javaFile.getClasses()) {
      String name = aClass.getName();
      if (name != null) {
        result.addElement(LookupElementBuilder.create(name).withIcon(aClass.getIcon(0)));
        String infix = getInfix(currentMatcher.getPrefix(), name);
        CompletionResultSet prefixed = null;
        if (infix == null) {
          if (invocationCount <= 0) continue;
        }
        else {
          String memberPrefix = currentMatcher.getPrefix().substring(name.length() + infix.length());
          prefixed = result.withPrefixMatcher(currentMatcher.cloneWithPrefix(memberPrefix));
        }
        for (PsiElement child = aClass.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (child instanceof PsiField || child instanceof PsiMethod || child instanceof PsiClass) {
            String memberName = ((PsiMember)child).getName();
            Icon icon = child.getIcon(0);
            if (memberName != null) {
              LookupElementBuilder element = LookupElementBuilder.create(memberName).withIcon(icon);
              if (invocationCount > 0) {
                result.addElement(element);
              }
              if (prefixed != null) {
                if (child instanceof PsiMethod || child instanceof PsiField && !infix.equals("::") || infix.equals(".")) {
                  prefixed.addElement(element);
                }
              }
            }
          }
        }
      }
    }
  }

  @Nullable
  private static String getInfix(String currentPrefix, String className) {
    if (!currentPrefix.startsWith(className)) return null;
    for (String infix : new String[]{".", "#", "::"}) {
      if (currentPrefix.startsWith(infix, className.length())) {
        return infix;
      }
    }
    return null;
  }
}

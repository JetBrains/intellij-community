// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaSymbolNameCompletionContributor implements SymbolNameCompletionContributor {
  @NotNull
  @Override
  public Collection<LookupElement> getLookupElements(@NotNull PsiFile file, int invocationCount, @NotNull String prefix) {
    PsiClassOwner javaFile = ObjectUtils.tryCast(file, PsiClassOwner.class);
    if (javaFile == null) return Collections.emptyList();
    List<LookupElement> result = new ArrayList<>();
    for (PsiClass aClass : javaFile.getClasses()) {
      String name = aClass.getName();
      if (name != null) {
        result.add(LookupElementBuilder.create(name).withIcon(aClass.getIcon(0)));
        String infix = getInfix(prefix, name);
        String memberPrefix = null;
        if (infix != null) {
          memberPrefix = prefix.substring(0, name.length() + infix.length());
        }
        else if (invocationCount <= 0) continue;
        processClassBody(invocationCount, result, aClass, infix, memberPrefix);
      }
    }
    return result;
  }

  protected void processClassBody(int invocationCount, List<LookupElement> result, PsiElement aClass, String infix, String memberPrefix) {
    for (PsiElement child = aClass.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiField || child instanceof PsiMethod || child instanceof PsiClass) {
        String memberName = ((PsiMember)child).getName();
        Icon icon = child.getIcon(0);
        if (memberName != null) {
          LookupElementBuilder element = LookupElementBuilder.create(memberName).withIcon(icon);
          if (invocationCount > 0) {
            result.add(element);
          }
          if (memberPrefix != null) {
            if (child instanceof PsiMethod || child instanceof PsiField && !infix.equals("::") || infix.equals(".")) {
              result.add(LookupElementBuilder.create(memberPrefix + memberName).withIcon(icon));
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

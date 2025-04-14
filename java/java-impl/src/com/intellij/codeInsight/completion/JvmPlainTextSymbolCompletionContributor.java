// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An implementation of {@link PlainTextSymbolCompletionContributor} which is suitable for JVM languages.
 */
public final class JvmPlainTextSymbolCompletionContributor implements PlainTextSymbolCompletionContributor {
  @Override
  public @NotNull @Unmodifiable Collection<LookupElement> getLookupElements(@NotNull PsiFile file, int invocationCount, @NotNull String prefix) {
    PsiClassOwner jvmFile = ObjectUtils.tryCast(file, PsiClassOwner.class);
    if (jvmFile == null) return Collections.emptyList();
    List<LookupElement> result = new ArrayList<>();
    for (PsiClass aClass : jvmFile.getClasses()) {
      String name = aClass.getName();
      if (name == null) continue;
      if (aClass instanceof SyntheticElement) {
        processSyntheticClass(result, aClass);
      }
      else {
        result.add(LookupElementBuilder.create(name).withIcon(aClass.getIcon(0)));
        String infix = getInfix(prefix, name);
        String memberPrefix = null;
        String rest = null;
        if (infix != null) {
          int offset = name.length() + infix.length();
          memberPrefix = prefix.substring(0, offset);
          rest = prefix.substring(offset);
        }
        else if (invocationCount <= 0) continue;
        processClassBody(invocationCount, result, aClass, infix, memberPrefix, rest);
      }
    }
    return result;
  }

  private static void processSyntheticClass(List<LookupElement> result, PsiClass aClass) {
    for (PsiMember[] members : new PsiMember[][] {aClass.getMethods(), aClass.getFields(), aClass.getInnerClasses()}) {
      for (PsiMember member : members) {
        if (!member.isPhysical()) continue;
        String memberName = member.getName();
        if (memberName == null) continue;
        Icon icon = member.getIcon(0);
        LookupElementBuilder element = LookupElementBuilder.create(memberName).withIcon(icon);
        result.add(element.withTailText(" in " + aClass.getContainingFile().getName(), true));
      }
    }
  }

  private static void processClassBody(int invocationCount,
                                       List<LookupElement> result,
                                       PsiClass aClass,
                                       String infix,
                                       String memberPrefix,
                                       String rest) {
    for (PsiMember[] members : new PsiMember[][] {aClass.getMethods(), aClass.getFields(), aClass.getInnerClasses()}) {
      for (PsiMember member : members) {
        if (!member.isPhysical()) continue;
        String memberName = member.getName();
        if (memberName == null) continue;
        Icon icon = member.getIcon(0);
        LookupElementBuilder element = LookupElementBuilder.create(memberName).withIcon(icon);
        if (invocationCount > 0) {
          result.add(element);
        }
        if (memberPrefix != null) {
          if (member instanceof PsiMethod || member instanceof PsiField && !infix.equals("::") || infix.equals(".")) {
            result.add(LookupElementBuilder.create(memberPrefix + memberName).withIcon(icon));
            if (member instanceof PsiClass) {
              String nestedInfix = getInfix(rest, memberName);
              if (nestedInfix != null) {
                int index = memberName.length() + nestedInfix.length();
                String nestedPrefix = memberPrefix+rest.substring(0, index);
                processClassBody(0, result, (PsiClass)member, nestedInfix, nestedPrefix, rest.substring(index));
              }
            }
          }
        }
      }
    }
  }

  private static @Nullable String getInfix(String currentPrefix, String className) {
    if (!currentPrefix.startsWith(className)) return null;
    for (String infix : new String[]{".", "#", "::"}) {
      if (currentPrefix.startsWith(infix, className.length())) {
        return infix;
      }
    }
    return null;
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ModifierChooser {
  private static final String[][] CLASS_MODIFIERS = {
    {PsiKeyword.PUBLIC},
    {PsiKeyword.FINAL, PsiKeyword.ABSTRACT}
  };

  private static final String[][] CLASS_15_MODIFIERS = {
    {PsiKeyword.PUBLIC},
    {PsiKeyword.FINAL, PsiKeyword.ABSTRACT},
    {PsiKeyword.FINAL, PsiKeyword.SEALED, PsiKeyword.NON_SEALED}
  };

  private static final String[][] CLASS_MEMBER_MODIFIERS = {
    {PsiKeyword.PUBLIC, PsiKeyword.PROTECTED, PsiKeyword.PRIVATE},
    {PsiKeyword.STATIC},
    {PsiKeyword.FINAL, PsiKeyword.ABSTRACT},
    {PsiKeyword.NATIVE},
    {PsiKeyword.SYNCHRONIZED},
    {PsiKeyword.STRICTFP},
    {PsiKeyword.VOLATILE},
    {PsiKeyword.TRANSIENT}
  };

  private static final String[][] INTERFACE_9_MEMBER_MODIFIERS = {
    {PsiKeyword.PUBLIC, PsiKeyword.PROTECTED, PsiKeyword.PRIVATE},
    {PsiKeyword.STATIC, PsiKeyword.DEFAULT},
    {PsiKeyword.FINAL, PsiKeyword.ABSTRACT}
  };

  private static final String[][] INTERFACE_8_MEMBER_MODIFIERS = {
    {PsiKeyword.PUBLIC, PsiKeyword.PROTECTED},
    {PsiKeyword.STATIC, PsiKeyword.DEFAULT},
    {PsiKeyword.FINAL, PsiKeyword.ABSTRACT}
  };

  private static final String[][] INTERFACE_MEMBER_MODIFIERS = {
    {PsiKeyword.PUBLIC, PsiKeyword.PROTECTED},
    {PsiKeyword.FINAL, PsiKeyword.ABSTRACT}
  };

  static String[] getKeywords(@NotNull PsiElement position) {
    final PsiModifierList list = findModifierList(position);
    if (list == null && !shouldSuggestModifiers(position)) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    PsiElement scope = position.getParent();
    while (scope != null) {
      if (scope instanceof PsiJavaFile) {
        return addClassModifiers(list, scope);
      }
      if (scope instanceof PsiClass) {
        return addMemberModifiers(list, ((PsiClass)scope).isInterface(), scope);
      }

      scope = scope.getParent();
      if (scope instanceof PsiDirectory) break;
    }
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  public static String[] addClassModifiers(PsiModifierList list, @NotNull PsiElement scope) {
    return addKeywords(list, PsiUtil.getLanguageLevel(scope).isAtLeast(LanguageLevel.JDK_15_PREVIEW) ? CLASS_15_MODIFIERS : CLASS_MODIFIERS);
  }

  public static String[] addMemberModifiers(PsiModifierList list, final boolean inInterface, @NotNull PsiElement position) {
    return addKeywords(list, inInterface ? getInterfaceMemberModifiers(position) : CLASS_MEMBER_MODIFIERS);
  }

  private static String[][] getInterfaceMemberModifiers(@NotNull PsiElement list) {
    if (PsiUtil.isLanguageLevel9OrHigher(list)) {
      return INTERFACE_9_MEMBER_MODIFIERS;
    }
    if (PsiUtil.isLanguageLevel8OrHigher(list)) {
      return INTERFACE_8_MEMBER_MODIFIERS;
    }
    return INTERFACE_MEMBER_MODIFIERS;
  }

  private static String[] addKeywords(PsiModifierList list, String[][] keywordSets) {
    final List<String> ret = new ArrayList<>();
    for (int i = 0; i < keywordSets.length; i++) {
      final String[] keywords = keywordSets[keywordSets.length - i - 1];
      boolean containModifierFlag = false;
      if (list != null) {
        for (@PsiModifier.ModifierConstant String keyword : keywords) {
          if (list.hasExplicitModifier(keyword)) {
            containModifierFlag = true;
            break;
          }
        }
      }
      if (!containModifierFlag) {
        ContainerUtil.addAll(ret, keywords);
      }
    }
    return ArrayUtilRt.toStringArray(ret);
  }

  @Nullable
  public static PsiModifierList findModifierList(@NotNull PsiElement element) {
    if(element.getParent() instanceof PsiModifierList) {
      return (PsiModifierList)element.getParent();
    }

    return PsiTreeUtil.getParentOfType(FilterPositionUtil.searchNonSpaceNonCommentBack(element), PsiModifierList.class);
  }

  private static boolean shouldSuggestModifiers(PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent instanceof PsiJavaCodeReferenceElement ||
           parent instanceof PsiErrorElement || parent instanceof PsiTypeElement ||
           parent instanceof PsiMethod || parent instanceof PsiVariable ||
           parent instanceof PsiDeclarationStatement || parent instanceof PsiImportList ||
           parent instanceof PsiDocComment) {
      parent = parent.getParent();
      if (parent instanceof JspClassLevelDeclarationStatement) {
        parent = parent.getContext();
      }
    }

    if (parent == null) return false;

    return (parent instanceof PsiJavaFile || parent instanceof PsiClass) &&
           JavaKeywordCompletion.isEndOfBlock(element);
  }
}

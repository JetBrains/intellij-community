// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.pom.java.JavaFeature;
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
import java.util.function.Predicate;

public final class ModifierChooser {
  /**
   * ArrayOfModifiers contains
   * @param modifiers which can't be use together.
   * @param allowToUse when modifiers are applicable, if null they can be used always
   */
  private record ArrayOfModifiers(@NotNull String @NotNull[] modifiers,
                                  @Nullable Predicate<@NotNull PsiElement> allowToUse){
    private ArrayOfModifiers(@NotNull String @NotNull[] modifiers) {
      this(modifiers, (Predicate<PsiElement>)null);
    }
    private ArrayOfModifiers(@NotNull String @NotNull[] modifiers, @NotNull JavaFeature feature) {
      this(modifiers, v->PsiUtil.isAvailable(feature, v));
    }
  }

  private static final ArrayOfModifiers[] CLASS_MODIFIERS = {
    new ArrayOfModifiers(new String[]{JavaKeywords.PUBLIC}),
    new ArrayOfModifiers(new String[]{JavaKeywords.FINAL, JavaKeywords.ABSTRACT})
  };

  private static final String[] CLASS_MODIFIERS_WITH_SEALED = {
    JavaKeywords.PUBLIC, JavaKeywords.FINAL, JavaKeywords.SEALED, JavaKeywords.NON_SEALED, JavaKeywords.ABSTRACT
  };

  private static final ArrayOfModifiers[] CLASS_MEMBER_MODIFIERS = {
    new ArrayOfModifiers(new String[]{JavaKeywords.PUBLIC, JavaKeywords.PROTECTED, JavaKeywords.PRIVATE}),
    new ArrayOfModifiers(new String[]{JavaKeywords.STATIC}),
    new ArrayOfModifiers(new String[]{JavaKeywords.FINAL, JavaKeywords.ABSTRACT}),
    new ArrayOfModifiers(new String[]{JavaKeywords.SEALED, JavaKeywords.NON_SEALED}, JavaFeature.SEALED_CLASSES),
    new ArrayOfModifiers(new String[]{JavaKeywords.NATIVE}),
    new ArrayOfModifiers(new String[]{JavaKeywords.SYNCHRONIZED}),
    new ArrayOfModifiers(new String[]{JavaKeywords.STRICTFP}, el -> !PsiUtil.isAvailable(JavaFeature.ALWAYS_STRICTFP, el)),
    new ArrayOfModifiers(new String[]{JavaKeywords.VOLATILE}),
    new ArrayOfModifiers(new String[]{JavaKeywords.TRANSIENT})
  };

  private static final ArrayOfModifiers[] FILE_MEMBER_MODIFIERS = {
    new ArrayOfModifiers(new String[]{JavaKeywords.PUBLIC}),
    new ArrayOfModifiers(new String[]{JavaKeywords.FINAL, JavaKeywords.ABSTRACT}),
    new ArrayOfModifiers(new String[]{JavaKeywords.STRICTFP}, el -> !PsiUtil.isAvailable(JavaFeature.ALWAYS_STRICTFP, el)),
    new ArrayOfModifiers(new String[]{JavaKeywords.SEALED, JavaKeywords.NON_SEALED}, JavaFeature.SEALED_CLASSES)
  };

  private static final ArrayOfModifiers[] INTERFACE_MEMBER_MODIFIERS = {
    new ArrayOfModifiers(new String[]{JavaKeywords.PUBLIC, JavaKeywords.PROTECTED},
                         el -> !PsiUtil.isAvailable(JavaFeature.PRIVATE_INTERFACE_METHODS, el)),
    new ArrayOfModifiers(new String[]{JavaKeywords.PUBLIC, JavaKeywords.PROTECTED, JavaKeywords.PRIVATE}, JavaFeature.PRIVATE_INTERFACE_METHODS),
    new ArrayOfModifiers(new String[]{JavaKeywords.FINAL, JavaKeywords.ABSTRACT}),
    new ArrayOfModifiers(new String[]{JavaKeywords.STATIC, JavaKeywords.DEFAULT}, JavaFeature.STATIC_INTERFACE_CALLS),
    new ArrayOfModifiers(new String[]{JavaKeywords.SEALED, JavaKeywords.NON_SEALED}, JavaFeature.SEALED_CLASSES)
  };

  static String[] getKeywords(@NotNull PsiElement position) {
    final PsiModifierList list = findModifierList(position);
    if (list == null && !shouldSuggestModifiers(position)) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    PsiElement scope = position.getParent();
    while (scope != null) {
      if (scope instanceof PsiJavaFile ||
          scope instanceof PsiClass ||
          scope.getParent() instanceof PsiImplicitClass) {
        return addJavaFileMemberModifiers(list, position);
      }
      if (scope.getParent() instanceof PsiClass psiClass) {
        PsiIdentifier identifier = psiClass.getNameIdentifier();
        if (identifier == null ||
            identifier.getTextRange().getStartOffset() < scope.getTextRange().getStartOffset()) {
          return addMemberModifiers(list, psiClass.isInterface(), psiClass);
        }
      }
      scope = scope.getParent();
      if (scope instanceof PsiDirectory) break;
    }
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  /**
   * @deprecated: it is not used.
   */
  @Deprecated(forRemoval = true)
  public static String[] addClassModifiers(PsiModifierList list, @NotNull PsiElement scope) {
    if (PsiUtil.isAvailable(JavaFeature.SEALED_CLASSES, scope)) {
      if (list == null) {
        return CLASS_MODIFIERS_WITH_SEALED.clone();
      }
      else {
        final List<String> ret = new ArrayList<>();
        addIfNotPresent(PsiModifier.PUBLIC, list, ret);
        if (!list.hasModifierProperty(PsiModifier.FINAL)) {
          boolean hasNonSealed = list.hasModifierProperty(PsiModifier.NON_SEALED);
          boolean hasSealed = list.hasModifierProperty(PsiModifier.SEALED);
          if (!hasNonSealed) {
            addIfNotPresent(PsiModifier.SEALED, list, ret);
          }
          if (!hasSealed) {
            addIfNotPresent(PsiModifier.NON_SEALED, list, ret);
          }
          boolean hasAbstract = list.hasModifierProperty(PsiModifier.ABSTRACT);
          if (!hasAbstract) {
            ret.add(PsiModifier.ABSTRACT);
            if (!hasNonSealed && !hasSealed) {
              ret.add(PsiModifier.FINAL);
            }
          }
        }
        return ArrayUtilRt.toStringArray(ret);
      }
    }
    return addKeywords(list, CLASS_MODIFIERS, scope);
  }

  private static void addIfNotPresent(String modifier, PsiModifierList list, List<String> ret) {
    if (!list.hasModifierProperty(modifier)) {
      ret.add(modifier);
    }
  }

  private static String[] addJavaFileMemberModifiers(@Nullable PsiModifierList list, @NotNull PsiElement position) {
    if (PsiUtil.isAvailable(JavaFeature.IMPLICIT_CLASSES, position) &&
        (position.getContainingFile() instanceof PsiJavaFile javaFile && javaFile.getPackageStatement() == null)) {
      return addMemberModifiers(list, false, position);
    }
    return addKeywords(list, FILE_MEMBER_MODIFIERS, position);
  }

  public static String[] addMemberModifiers(PsiModifierList list, final boolean inInterface, @NotNull PsiElement position) {
    return addKeywords(list, inInterface ? INTERFACE_MEMBER_MODIFIERS : CLASS_MEMBER_MODIFIERS, position);
  }

  private static String[] addKeywords(PsiModifierList list, ArrayOfModifiers[] keywordSets, @NotNull PsiElement position) {
    final List<String> ret = new ArrayList<>();
    for (int i = 0; i < keywordSets.length; i++) {
      final ArrayOfModifiers keywords = keywordSets[keywordSets.length - i - 1];
      if (keywords.allowToUse() != null && !keywords.allowToUse().test(position)) {
        continue;
      }
      boolean containModifierFlag = false;
      if (list != null) {
        for (@PsiModifier.ModifierConstant String keyword : keywords.modifiers()) {
          if (list.hasExplicitModifier(keyword)) {
            containModifierFlag = true;
            break;
          }
        }
      }
      if (!containModifierFlag) {
        ContainerUtil.addAll(ret, keywords.modifiers());
      }
    }
    return ArrayUtilRt.toStringArray(ret);
  }

  public static @Nullable PsiModifierList findModifierList(@NotNull PsiElement element) {
    if (element.getParent() instanceof PsiModifierList) {
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

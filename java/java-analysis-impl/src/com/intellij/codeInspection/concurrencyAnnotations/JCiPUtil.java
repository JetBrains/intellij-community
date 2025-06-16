// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ConcurrencyAnnotationsManager;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class JCiPUtil {

  private JCiPUtil() {}

  public static boolean isJCiPAnnotation(String ref) {
    return "Immutable".equals(ref) || "GuardedBy".equals(ref) || "ThreadSafe".equals(ref) || "NotThreadSafe".equals(ref);
  }

  public static boolean isImmutable(@NotNull PsiClass aClass) {
    return isImmutable(aClass, true);
  }

  public static boolean isImmutable(@NotNull PsiClass aClass, boolean checkDocComment) {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aClass, ConcurrencyAnnotationsManager.getInstance(aClass.getProject()).getImmutableAnnotations());
    if (annotation != null) {
      return true;
    }
    if (checkDocComment && containsImmutableWord(aClass.getContainingFile())) {
      final PsiDocComment comment = aClass.getDocComment();
      return comment != null && comment.findTagByName("@Immutable") != null;
    }
    return false;
  }

  private static boolean containsImmutableWord(PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () ->
      CachedValueProvider.Result.create(PsiSearchHelper.getInstance(file.getProject()).hasIdentifierInFile(file, "Immutable"), file));
  }

  public static @Nullable String findGuardForMember(@NotNull PsiMember member) {
    List<String> annotations = ConcurrencyAnnotationsManager.getInstance(member.getProject()).getGuardedByAnnotations();
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(member, annotations);
    if (annotation != null) {
      return getGuardValue(annotation);
    }
    if (member instanceof PsiDocCommentOwner commentOwner) {
      PsiDocComment comment = commentOwner.getDocComment();
      if (comment != null) {
        PsiDocTag[] tags = comment.getTags();
        for (int i = tags.length - 1; i >= 0; i--) {
          String value = getGuardValue(tags[i]);
          if (value != null) {
            return value;
          }
        }
      }
    }
    return null;
  }

  static boolean isGuardedBy(@NotNull PsiMember member, @NotNull String guard) {
    final List<String> annotations = ConcurrencyAnnotationsManager.getInstance(member.getProject()).getGuardedByAnnotations();
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(member, annotations);
    return annotation != null && guard.equals(getGuardValue(annotation));
  }

  static boolean isGuardedByAnnotation(@NotNull PsiAnnotation annotation) {
    return ConcurrencyAnnotationsManager.getInstance(annotation.getProject()).getGuardedByAnnotations().contains(annotation.getQualifiedName());
  }

  static boolean isGuardedByTag(PsiDocTag tag) {
    return tag.getText().startsWith("@GuardedBy");
  }

  static @Nullable String getGuardValue(PsiAnnotation annotation) {
    final PsiAnnotationMemberValue psiAnnotationMemberValue = annotation.findAttributeValue("value");
    if (psiAnnotationMemberValue instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression)psiAnnotationMemberValue).getValue();
      if (value instanceof String) {
        return resolveItself((String)value, annotation);
      }
    }
    return null;
  }

  static @Nullable String getGuardValue(PsiDocTag tag) {
    if ("GuardedBy".equals(tag.getName())) {
      final PsiDocTagValue value = tag.getValueElement();
      if (value == null) return "";
      return resolveItself(value.getText(), tag);
    }
    else {
      final String text = tag.getText();
      if (!text.startsWith("@GuardedBy")) return null;
      int start = text.indexOf('(');
      int end = text.indexOf(')');
      if (start >= end || start < 0) return "";
      return resolveItself(text.substring(start + 1, end), tag);
    }
  }

  private static String resolveItself(String value, PsiElement context) {
    if ("itself".equals(value)) {
      final PsiMember member = PsiTreeUtil.getParentOfType(context, PsiMember.class);
      if (!(member instanceof PsiField)) {
        return "itself";
      }
      return member.getName();
    }
    return value;
  }
}

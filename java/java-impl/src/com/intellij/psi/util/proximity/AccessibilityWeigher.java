// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.StaticAnalysisAnnotationManager;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NotNull;

public class AccessibilityWeigher extends ProximityWeigher {
  @Override
  public AccessibilityLevel weigh(@NotNull PsiElement element, @NotNull ProximityLocation location) {
    if (element instanceof PsiDocCommentOwner) {
      final PsiDocCommentOwner member = (PsiDocCommentOwner)element;
      PsiElement position = location.getPosition();
      if (position != null && !JavaPsiFacade.getInstance(member.getProject()).getResolveHelper().isAccessible(member, position, null)) {
        return AccessibilityLevel.INACCESSIBLE;
      }
      if (JavaCompletionUtil.isEffectivelyDeprecated(member)) return AccessibilityLevel.DEPRECATED;
      if (AnnotationUtil.isAnnotated(member, StaticAnalysisAnnotationManager.getInstance().getKnownUnstableApiAnnotations(), 0)) {
        return AccessibilityLevel.DISCOURAGED;
      }
      if (member instanceof PsiClass) {
        PsiFile file = member.getContainingFile();
        if (file instanceof PsiJavaFile) {
          String packageName = ((PsiJavaFile)file).getPackageName();
          if (packageName.startsWith("com.sun.") || packageName.startsWith("sun.") || packageName.startsWith("org.omg.")) {
            return AccessibilityLevel.DISCOURAGED;
          }
        }
        if ("java.awt.List".equals(((PsiClass)member).getQualifiedName())) {
          return AccessibilityLevel.DISCOURAGED;
        }
      }
    }
    return AccessibilityLevel.NORMAL;
  }

  public enum AccessibilityLevel {
    INACCESSIBLE,
    DEPRECATED,
    DISCOURAGED,
    NORMAL
  }
}

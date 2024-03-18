// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util.proximity;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.StaticAnalysisAnnotationManager;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.ProximityLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class AccessibilityWeigher extends ProximityWeigher {
  @Override
  public AccessibilityLevel weigh(@NotNull PsiElement element, @NotNull ProximityLocation location) {
    if (element instanceof PsiDocCommentOwner member) {
      PsiElement position = location.getPosition();
      Project project = location.getProject();
      if (project != null && !DumbService.isDumb(project)) {
        if (position != null && !JavaPsiFacade.getInstance(project).getResolveHelper().isAccessible(member, position, null)) {
          return AccessibilityLevel.INACCESSIBLE;
        }
        if (JavaCompletionUtil.isEffectivelyDeprecated(member)) return AccessibilityLevel.DEPRECATED;
        if (AnnotationUtil.isAnnotated(member, Arrays.asList(StaticAnalysisAnnotationManager.getInstance().getKnownUnstableApiAnnotations()), 0)) {
          return AccessibilityLevel.DISCOURAGED;
        }
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
